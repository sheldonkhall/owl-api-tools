package align;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExactMatcher {
	
	private Map<OWLClass,Set<OWLClass>> newOntoToSnomedMatches;
	private Set<OWLClass> unmatchedClasses;

	public ExactMatcher() {
		newOntoToSnomedMatches = new HashMap<>();
		unmatchedClasses = new HashSet<>();
	}

	public Map<OWLClass,Set<OWLClass>> getNewOntoToSnomedMatches() { return newOntoToSnomedMatches; }
	public Set<OWLClass> getUnmatchedClasses() { return unmatchedClasses; }

	public void compareSetsOfClasses(Map<OWLClass,String> targetOntoClassesToLabels, Map<OWLClass,Set<String>> baseOntoClassesToLabels) throws OWLOntologyCreationException {
		
		System.out.println("Building Index...");
		Map<String,Set<OWLClass>> invertedIndex = buildInvertedIndex(baseOntoClassesToLabels);
		
		for (Map.Entry<OWLClass,String> entry : targetOntoClassesToLabels.entrySet()) {
			Set<OWLClass> possibleMatches = invertedIndex.get(entry.getValue());
			if (possibleMatches == null )
				unmatchedClasses.add(entry.getKey());
			else
				newOntoToSnomedMatches.put(entry.getKey(),possibleMatches);
		}
	}

	private Map<String, Set<OWLClass>> buildInvertedIndex(Map<OWLClass, Set<String>> classesToLabels) {
		Map<String,Set<OWLClass>> invertedIndex = new HashMap<>();
		
		for (Map.Entry<OWLClass,Set<String>> entry : classesToLabels.entrySet()) {
			for (String classLabel : entry.getValue()) {
				Set<OWLClass> classesWithThisLabel = invertedIndex.computeIfAbsent(classLabel.toLowerCase(), k -> new HashSet<>());
				classesWithThisLabel.add(entry.getKey());
			}
		}
		return invertedIndex;
	}
	
//	private void compareOntologiesFromKB(String kbIRI, String newOntology, String filterBySTY) throws OWLOntologyCreationException {
//	OWLOntologyManager newOntoMan =  OWLManager.createOWLOntologyManager();
//	System.out.println("Loading ontology: " + newOntology);
//	OWLOntology newOnto = newOntoMan.loadOntologyFromOntologyDocument(new File(newOntology));
//	
//	System.out.println(newOnto.getClassesInSignature().size());
//	
//	ConnectionManager connection = new ConnectionManager(kbIRI);
//	String queryString = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + 
//			"PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n" + 
//			"select ?s ?prefL ?altL where { \n" + 
//			"	?s rdfs:subClassOf <" + filterBySTY + "> .\n" + 
//			"    ?s skos:prefLabel ?prefL .\n" + 
//			"    ?s skos:altLabel ?altL .\n" + 
//			"}";
//	BindingSet solution = null;
//	TupleQuery query = connection.prepareTupleQuery(queryString);
//	
//	try (TupleQueryResult result = query.evaluate()) {
//		while (result.hasNext()) {
//			solution = result.next();
//			String code = solution.getValue("c").stringValue();
//		}
//		query.clearBindings();
//	}
//	
//	
//	//We may use Anchoring to Restrict the part of SNOMED for which we care about.
//	Set<OWLClass> classesToIterateOver = new HashSet<OWLClass>(baseOnto.getClassesInSignature());
//	Map<OWLClass,Set<String>> classesToLabels = collectSnomedConceptLabels(classesToIterateOver,baseOnto);
//	
//	Map<OWLClass,String> targetOntoClassesToLabels = collectTargetOntoLabels(newOnto);
//	compareSetsOfClasses(targetOntoClassesToLabels,classesToLabels);
//}

}
