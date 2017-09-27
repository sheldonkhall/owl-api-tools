package align;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;
import utils.ConnectionManager;
import utils.I_Sub;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CompareOntoToKB {
	private ConnectionManager connManager;

	public static void main(String[] args) throws OWLOntologyCreationException, IOException {
		String path = "/Users/giorgos.stoilos/Documents/Ontologies/";
		String sourceOnto = null;
//		sourceOnto = "S3-Ontologies/symp/symp.owl";
		sourceOnto = "S3-Ontologies/doid/doid_2016-01-07.owl";
//		sourceOnto = "S3-Ontologies/bio-portal/Disease core ontology applied to Rare Diseases.rdf";
//		sourceOnto = "mine/carre_risk-factors.owl";
		String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/SnomedCT_InternationalRF2_Production_20170131T120000/snomedct_owl.owl";

		String kbIRI = "http://beauty:18915/repositories/clinicalKnowledge";
		CompareOntoToKB ontoInspector = new CompareOntoToKB(kbIRI);

		//Comparing using the KB
//		ontoInspector.checkOntologyAgainstKB(path + sourceOnto);

		//First week attempts using I_Sub
//		ontoInspector.checkOntologyAgainstAnotherSourceFile(baseOntology, path + sourceOnto);

		ontoInspector.shutDown();

		/** Compare to absorb relations */
//		sourceOnto = "mine/omim/OMIM.ttl";
//		CompareOntoToKB ontoInspector = new CompareOntoToKB();
//		String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/SnomedCT_InternationalRF2_Production_20170131T120000/snomedct_owl.owl";
//		ontoInspector.checkPosibilityToIngestRelations(baseOntology, path + sourceOnto);

//		String sourceFile = "mine/omim/NewRelations.txt";
//		new CompareOntoToKB().buildFileWithPrefLabels(baseOntology, path + sourceFile);
	}

	public CompareOntoToKB(String kbIRI) {
		connManager = new ConnectionManager(kbIRI);
	}

	public CompareOntoToKB() {
	}

	public void buildFileWithPrefLabels(String baseOntology, String file) throws IOException, OWLOntologyCreationException {
		BufferedReader br = null;
		FileReader fr = null;

		fr = new FileReader(file);
		br = new BufferedReader(fr);

		String sCurrentLine;

		OWLOntologyManager sourceMan =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading ontology: " + baseOntology);
		OWLOntology baseOnto = sourceMan.loadOntologyFromOntologyDocument(new File(baseOntology));

		Map<OWLClass,Set<String>> classesToLabels = collectAllLabels(baseOnto.getClassesInSignature(),baseOnto);

		OWLDataFactory factory = sourceMan.getOWLDataFactory();
		File outputFile = new File("RelationsWithStrings.tsv");
		PrintWriter writer = new PrintWriter( outputFile, "UTF-8");
		while ((sCurrentLine = br.readLine()) != null) {
			String[] splitedLine = sCurrentLine.split(" ");
			writer.println( classesToLabels.get(factory.getOWLClass(splitedLine[0])).iterator().next() + " " + splitedLine[1] + " " + classesToLabels.get(factory.getOWLClass(splitedLine[2])).iterator().next());

		}
		br.close();
		writer.close();
	}

	private void shutDown() {
		connManager.shutDown();
	}

	/**
	 * Takes two ontologies and goes through the relations that exist between the concepts of the new ontology. Then, each pair of these concepts it tries to match it using string similarity-based
	 * alignment to concepts of the baseOntology. If both of them match then this means that this relation can be transfered to the pair of concepts of the base ontology.
	 * @param baseOntology
	 * @param newOntology
	 * @throws OWLOntologyCreationException
	 */
	public void checkPosibilityToIngestRelations(String baseOntology, String newOntology) throws OWLOntologyCreationException {
		OWLOntologyManager newOntoMan =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading ontology: " + newOntology);
		OWLOntology newOnto = newOntoMan.loadOntologyFromOntologyDocument(new File(newOntology));

		Set<OWLIndividual> codesForWhichWeCare = new HashSet<>();
		int interrupt =0;
		for (OWLObjectPropertyAssertionAxiom axiom : newOnto.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
			if (axiom.getProperty().asOWLObjectProperty().getIRI().getIRIString().contains("http://purl.bioontology.org/ontology/OMIM/has_manifestation")) {
				if (interrupt++ > 500)
					break;
				codesForWhichWeCare.add(axiom.getSubject());
				codesForWhichWeCare.add(axiom.getObject());
			}
		}
		System.out.println(codesForWhichWeCare.size());
		OWLOntologyManager sourceMan =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading ontology: " + baseOntology);
		OWLOntology baseOnto = sourceMan.loadOntologyFromOntologyDocument(new File(baseOntology));

		//Match entities of the newOntology that we care about to the baseOntology.
		OWLDataFactory factory = newOntoMan.getOWLDataFactory();
		Set<OWLClass> classesToIterateOver = new HashSet<OWLClass>(baseOnto.getClassesInSignature());
		Map<OWLClass,Set<String>> classesToLabels = collectAllLabels(classesToIterateOver,baseOnto);
		long conceptsProcessed = 1;
		long totalNumOfConcepts = codesForWhichWeCare.size();
		Map<String,String> pairsOfMatches = new HashMap<>();
		Set<String> unmatchedClasses = new HashSet<>();
		Set<String> matchesFoundInTarget = new HashSet<>();
		for (OWLIndividual targetIndv : codesForWhichWeCare) {
			String classLabel = null;
			System.out.println("Processing concept " + conceptsProcessed++ + " of " + totalNumOfConcepts);

			/** HEURISTIC: Skip Non-leafts */
//			Set<OWLSubClassOfAxiom> axioms = newOnto.getSubClassAxiomsForSuperClass(targetClass);
//			if (!axioms.isEmpty()) {
//				System.out.println("skipping " + targetClass);
//				continue;
//			}
			OWLClass targetClass = factory.getOWLClass(targetIndv.asOWLNamedIndividual().getIRI());
			for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(targetClass, newOnto).collect(Collectors.toSet())) {
				if (!a.getProperty().toString().equals("rdfs:label") && !a.getProperty().getIRI().equals(SKOSVocabulary.PREFLABEL.getIRI()))
					continue;
				classLabel = a.getValue().asLiteral().get().getLiteral();
			}
			if (classLabel==null) {
				//class did not have a label. Take the fragment of its IRI
				String iriBreak = "#";
				if (!targetClass.getIRI().getIRIString().contains("#"))
					iriBreak = "/";
				classLabel = targetClass.getIRI().getIRIString().substring(targetClass.getIRI().getIRIString().lastIndexOf(iriBreak)+1);
			}
			boolean foundMatchingClass = false;
//			Set<OWLClass> classesToIterate = !classLabel.contains(" ") ? singleTokenClasses : restOfClasses;  
			for (OWLClass baseClass : classesToLabels.keySet()) {
				if (matchesFoundInTarget.contains(baseClass.getIRI().getIRIString()))
					continue;
				for (String targetLabel : classesToLabels.get(baseClass)) {
					if (I_Sub.score(classLabel, targetLabel, true)>0.85) {
						foundMatchingClass = true;
						pairsOfMatches.put(targetClass.getIRI().getIRIString(), baseClass.getIRI().getIRIString());
						System.out.println("Match found for: " + targetClass.getIRI().getIRIString() + " " + classLabel + " match " + baseClass.getIRI().getIRIString() + " " + targetLabel);
						matchesFoundInTarget.add(baseClass.getIRI().getIRIString());
						break;
					}
				}
				if (foundMatchingClass)
					break;
			}
			if (!foundMatchingClass) {
				System.out.println("Nothing found for: " + targetClass.getIRI().getIRIString() + " " + classLabel);
				unmatchedClasses.add(targetClass.getIRI().getIRIString() + " " + classLabel);
			}
		}

		for (String match : pairsOfMatches.keySet()) {
			OWLIndividual indvNew = factory.getOWLNamedIndividual(match);
			for (OWLObjectPropertyAssertionAxiom axiom : newOnto.getObjectPropertyAssertionAxioms(indvNew)) {
				if (axiom.getProperty().asOWLObjectProperty().getIRI().getIRIString().contains("http://purl.bioontology.org/ontology/OMIM/has_manifestation")) {
					OWLIndividual objectOfAssertion = axiom.getObject();
					if (pairsOfMatches.containsKey(objectOfAssertion.asOWLNamedIndividual().getIRI().getIRIString()))
						System.out.println(pairsOfMatches.get(match) + " hasManifestation " + pairsOfMatches.get(objectOfAssertion.asOWLNamedIndividual().getIRI().getIRIString()) );
				}
			}
		}

	}

	public void checkOntologyAgainstAnotherSourceFile(String baseOntology, String newOntology) throws OWLOntologyCreationException, FileNotFoundException, UnsupportedEncodingException {

		OWLOntologyManager newOntoMan =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading ontology: " + newOntology);
		OWLOntology newOnto = newOntoMan.loadOntologyFromOntologyDocument(new File(newOntology));

		OWLOntologyManager sourceMan =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading ontology: " + baseOntology);
		OWLOntology baseOnto = sourceMan.loadOntologyFromOntologyDocument(new File(baseOntology));

		Map<String,String> pairsOfMatches = new HashMap<>();
		Set<String> unmatchedClasses = new HashSet<>();
		Set<String> matchesFoundInTarget = new HashSet<>();

		Set<OWLClass> classesToIterateOver = new HashSet<OWLClass>(baseOnto.getClassesInSignature());

		/**
		 * Filtering optimisation according to semantic Types in order to reduce the inner loop below. E.g., for Disease ontology only get those SNOMED concepts that are subclasses of snmd:Disease.
		 */
//		Set<String> filterByCodes = fetchCodesOfSpecificTypeFromKB();
//		for (OWLClass baseClass : baseOnto.getClassesInSignature()) {
//			if (!filterByCodes.contains(baseClass.getIRI().getIRIString().substring(baseClass.getIRI().getIRIString().lastIndexOf("/")+1)) )
//				classesToIterateOver.remove(baseClass);
//		}
//		System.out.println(classesToIterateOver.size());

		Map<OWLClass,Set<String>> classesToLabels = collectAllLabels(classesToIterateOver,baseOnto);

//		/** Do some bucketing */
//		Set<OWLClass> singleTokenClasses = new HashSet<>();
//		Set<OWLClass> restOfClasses = new HashSet<>();
//		for(OWLClass owlClass : classesToIterateOver) {
//			int bucket = determineBucket(owlClass,classesToLabels.get(owlClass));
//			if (bucket == 1)
//				singleTokenClasses.add(owlClass);
//		}
//		System.out.println(singleTokenClasses.size());
//		restOfClasses.addAll(classesToLabels.keySet());
//		restOfClasses.removeAll(singleTokenClasses);
		System.out.println(classesToLabels.keySet().size());

		long conceptsProcessed = 1;
		long totalNumOfConcepts = newOnto.getClassesInSignature().size();

		for (OWLClass targetClass : newOnto.getClassesInSignature()) {
			String classLabel = null;
			System.out.println("Processing concept " + conceptsProcessed++ + " of " + totalNumOfConcepts);

			/** HEURISTIC: Skip Non-leafts */
//			Set<OWLSubClassOfAxiom> axioms = newOnto.getSubClassAxiomsForSuperClass(targetClass);
//			if (!axioms.isEmpty()) {
//				System.out.println("skipping " + targetClass);
//				continue;
//			}
			for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(targetClass, newOnto).collect(Collectors.toSet())) {
				if (!a.getProperty().toString().equals("rdfs:label") && !a.getProperty().getIRI().equals(SKOSVocabulary.PREFLABEL.getIRI()))
					continue;
				classLabel = a.getValue().asLiteral().get().getLiteral();
			}
			if (classLabel==null) {
				//class did not have a label. Take the fragment of its IRI
				String iriBreak = "#";
				if (!targetClass.getIRI().getIRIString().contains("#"))
					iriBreak = "/";
				classLabel = targetClass.getIRI().getIRIString().substring(targetClass.getIRI().getIRIString().lastIndexOf(iriBreak)+1);
			}
			boolean foundMatchingClass = false;
//			Set<OWLClass> classesToIterate = !classLabel.contains(" ") ? singleTokenClasses : restOfClasses;  
			for (OWLClass baseClass : classesToLabels.keySet()) {
				if (matchesFoundInTarget.contains(baseClass.getIRI().getIRIString()))
					continue;
				for (String targetLabel : classesToLabels.get(baseClass)) {
					if (I_Sub.score(classLabel, targetLabel, true)>0.85) {
						foundMatchingClass = true;
						pairsOfMatches.put(targetClass.getIRI().getIRIString(), baseClass.getIRI().getIRIString());
						System.out.println("Match found for: " + targetClass.getIRI().getIRIString() + " " + classLabel + " match " + baseClass.getIRI().getIRIString() + " " + targetLabel);
						matchesFoundInTarget.add(baseClass.getIRI().getIRIString());
						break;
					}
				}
				if (foundMatchingClass)
					break;
			}
			if (!foundMatchingClass) {
				System.out.println("Nothing found for: " + targetClass.getIRI().getIRIString() + " " + classLabel);
				unmatchedClasses.add(targetClass.getIRI().getIRIString() + " " + classLabel);
			}
		}

		System.out.println("Found matches for: " + pairsOfMatches.keySet().size() + " out of " + totalNumOfConcepts);
		File outputFile = new File("MatchesFound.tsv");
		PrintWriter writer = new PrintWriter( outputFile, "UTF-8");
		pairsOfMatches.entrySet().stream().forEach(e -> writer.println(e.getKey() + "\t" + e.getValue()));
		writer.close();

		outputFile = new File("NotMatched.tsv");
		PrintWriter newWriter = new PrintWriter( outputFile, "UTF-8");
		unmatchedClasses.stream().forEach(e -> newWriter.println(e));
		newWriter.close();
	}

	private Map<OWLClass, Set<String>> collectAllLabels(Set<OWLClass> classesToIterateOver, OWLOntology baseOntology) {
		Map<OWLClass,Set<String>> classesToLabels = new HashMap<>();
		for (OWLClass baseClass : classesToIterateOver) {
			for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(baseClass, baseOntology).collect(Collectors.toSet())) {
				Set<String> labels = classesToLabels.computeIfAbsent(baseClass, k -> new HashSet<>());
				if (a.getValue().asLiteral().isPresent()) {
					labels.add(a.getValue().asLiteral().get().getLiteral().replaceAll(" \\(.*?\\)", ""));
				}
			}
		}
		return classesToLabels;
	}

	private int determineBucket(String owlClassAsString) {
		return owlClassAsString.split(" ").length;
	}

	private int determineBucket(OWLClass owlClass, Set<String> labelsOfClass) {
		int bucket=0;
		for (String classLabel : labelsOfClass) {
			int tokens = determineBucket(classLabel);
			if (tokens > bucket)
				bucket = tokens;
		}
		return bucket;
	}

	private Set<String> fetchCodesOfSpecificTypeFromKB() {
		Set<String> codes = new HashSet<String>();
		String queryString = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
				"select ?c where { \n" +
				"    ?c rdfs:subClassOf <http://kb.babylonhealth.com/snomedct-20170131T120000/64572001> .\n" +
				"}";

		BindingSet solution = null;
		TupleQuery query = connManager.prepareTupleQuery(queryString);

		try (TupleQueryResult result = query.evaluate()) {
			while (result.hasNext()) {
				solution = result.next();
				String code = solution.getValue("c").stringValue();
				codes.add(code.substring(code.lastIndexOf("/")+1));
			}
			query.clearBindings();
		}
		System.out.println("number of codes found " + codes.size());
		return codes;
	}

	public void checkOntologyAgainstKB(String ontoFile) throws OWLOntologyCreationException {
		OWLOntologyManager sourceMan =  OWLManager.createOWLOntologyManager();
		System.out.println("Loading ontology: " + ontoFile);
		OWLOntology onto = sourceMan.loadOntologyFromOntologyDocument(new File(ontoFile));

		//Filter the concepts of the input ontology according to some measure of informativeness.
//		Set<String> informativeClasses = onto.classesInSignature().filter( e -> isInformativeConcept(e)).collect(Collectors.toSet());

		long conceptsProcessed = 1;
		long totalNumOfConcepts = onto.getClassesInSignature().size();

		System.out.println(onto.getClassesInSignature().size());
		for (OWLClass c : onto.getClassesInSignature()) {
			System.out.println("Processing concept " + conceptsProcessed++ + " of " + totalNumOfConcepts);
			for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(c, onto).collect(Collectors.toSet())) {
				if (!a.getProperty().toString().equals("rdfs:label"))
					continue;
		        if (a.getValue().asLiteral().isPresent()) {
		        		String matchingCode = checkConceptAgainstKB(a.getValue().asLiteral().get().getLiteral());
		        		if (matchingCode==null)
		        			System.out.println("Nothing found for: " + c.getIRI().getIRIString() + " " + a.getValue().asLiteral().get().getLiteral());
		        		else
		        			System.out.println("Match found for: " + c.getIRI().getIRIString() + " " + a.getValue().asLiteral().get().getLiteral() + " " + matchingCode);
		        }
			}
		}
	}

	private String checkConceptAgainstKB(String literal) {
		String[] stringTokens = literal.split(" ");

		//Send SPARQL queries to the KB to check what is already in there
		String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n " +
					"	PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
					"	PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n" +
					"	select ?s ?l ?l1 where {   ?s rdf:type owl:Class . \n " +
					"	{ ?s skos:prefLabel ?l .\n ";
		for (int i=0 ; i<stringTokens.length ; i++ ) {
			if (stringTokens[i].length()<3)
				continue;
			queryString += "FILTER contains(lcase(str(?l)) , \"" + stringTokens[i] + "\") .";
		}
		queryString += "} UNION\n";

		queryString += "	{ ?s skos:altLabel ?l1 .\n ";
		for (int i=0 ; i<stringTokens.length ; i++ ) {
			if (stringTokens[i].length()<4)
				continue;
			queryString += "FILTER contains(lcase(str(?l1)) , \"" + stringTokens[i] + "\") .";
		}
		queryString += " }\n }";

		System.out.println(queryString);

		BindingSet solution = null;
		TupleQuery query = connManager.prepareTupleQuery(queryString);

		try (TupleQueryResult result = query.evaluate()) {
			while (result.hasNext()) {
				solution = result.next();
				return solution.getValue("s").stringValue();
			}
			query.clearBindings();
		}
		return null;
	}
}
