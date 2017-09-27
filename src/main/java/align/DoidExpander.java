package align;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by giorgos.stoilos on 25/09/2017.
 */
public class DoidExpander extends GeneralExpander {

  private static String kbIRI;

  private String DOID_ONTO_ANCHOR_IN_SNOMED = SNOMED_CLASS_IRI + "64572001";

  public DoidExpander(String snomedDocumentFile, String kbIRI) throws OWLOntologyCreationException {
    super(snomedDocumentFile);
    this.kbIRI = kbIRI;
  }

  public void compareOntologiesFromFiles(String newOntology, String filterBySTY) throws OWLOntologyCreationException {
    //Beware of missmatches in kb vs source ontology iri-prefixes.
    String kbIRIPrefix = filterBySTY.substring(0,filterBySTY.lastIndexOf('/'));
    String sourceFilePrefix = snomedOntology.getOntologyID().getOntologyIRI().get().getIRIString();
    sourceFilePrefix = "http://snomed.info/id/";
    sourceFilePrefix = sourceFilePrefix.substring(0,sourceFilePrefix.lastIndexOf('/'));

    //prune the set of classes from SNOMED for which we care according to some given semantic type, e.g., match only to Diseases.
//    Set<OWLClass> classesToIterateOver = new HashSet<>();
//    OWLDataFactory owlFactory = snomedOntology.getOWLOntologyManager().getOWLDataFactory();
//    ConnectionManager connection = new ConnectionManager(kbIRI);
//    String queryString =  "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
//                          "select ?s where { \n" +
//                          "	?s rdfs:subClassOf <" + filterBySTY + "> .\n" +
//                          "}";
//    BindingSet solution = null;
//    TupleQuery query = connection.prepareTupleQuery(queryString);
//
//    try (TupleQueryResult result = query.evaluate()) {
//      while (result.hasNext()) {
//        solution = result.next();
//        String code = solution.getValue("s").stringValue();
//        OWLClass owlClass = owlFactory.getOWLClass(code.replaceAll(kbIRIPrefix, sourceFilePrefix));
//        classesToIterateOver.add(owlClass);
//      }
//      query.clearBindings();
//    }
//    System.out.println("Cl to iterate: " + classesToIterateOver.size());
//    connection.shutDown();
//
//    snomedClassesToMatchToLabels = snomedClassesToMatchToLabels.entrySet().stream().filter(e -> classesToIterateOver.contains(e.getKey())).collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));

    OWLOntologyManager newOntoMan =  OWLManager.createOWLOntologyManager();
    OWLOntology newOnto = newOntoMan.loadOntologyFromOntologyDocument(new File(newOntology));
    targetOntoClassesToLabels = collectTargetOntoLabels(newOnto);

    exactMatcher.compareSetsOfClasses(targetOntoClassesToLabels, snomedClassesToMatchToLabels);
  }

  public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
    String path = "/Users/giorgos.stoilos/Documents/Ontologies/";
	String sourceOnto = "S3-Ontologies/doid/doid_2016-01-07.owl";

    //Snomed Source
//    String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/SnomedCT_InternationalRF2_Production_20170131T120000/snomedct_owl.owl";
    String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/extSnomed.owl";

    //When matching an ontology we can restrict to some parts of it. E.g., if we match the Symptom ontology we may only be interested to
    //compare to the part of SNOMED that corresponds to findings, or if we want to import a Gene ontology then only to the part of snomed that has Genes.
	String filterBySTY = "http://kb.babylonhealth.com/snomedct-20170131T120000/64572001";

    DoidExpander doidExpander = new DoidExpander(baseOntology,"http://beauty:18915/repositories/clinicalKnowledge");
    doidExpander.compareOntologiesFromFiles(path + sourceOnto, filterBySTY);
//    doidExpander.printResult();

    doidExpander.expandSnomed();
  }

  @Override
  public void expandSnomed() throws OWLOntologyCreationException, OWLOntologyStorageException {
    System.out.println("extending snomed using doid ontology...");
    extendedSnomed = extendedSnomedManager.createOntology(snomedOntology.getOntologyID().getOntologyIRI().get());
    extendedSnomedManager.addAxioms(extendedSnomed,snomedOntology.getAxioms());

    System.out.println("adding new classes");
    addNewConcepts(exactMatcher.getUnmatchedClasses());
    System.out.println("adding new labels/definitions");
    addNewLabels(exactMatcher.getNewOntoToSnomedMatches());

    String pathToSave = "/Users/giorgos.stoilos/Documents/Ontologies/extSnomed-symp-doid.owl";
    File fileFormatted = new File(pathToSave);
    extendedSnomedManager.saveOntology(extendedSnomed, new OWLXMLDocumentFormat(), IRI.create(fileFormatted.toURI()));
  }

  private void addNewConcepts(Set<OWLClass> unmatchedClasses) {
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    OWLClass diseaseClass = factory.getOWLClass(IRI.create(DOID_ONTO_ANCHOR_IN_SNOMED));

    for (OWLClass unmatchedClass : unmatchedClasses) {
      String oldIRIPrefix = unmatchedClass.getIRI().getIRIString();
      oldIRIPrefix = oldIRIPrefix.substring(0,oldIRIPrefix.lastIndexOf("/")+1);
      IRI iriOfNewClass =  IRI.create(unmatchedClass.getIRI().getIRIString().replace(oldIRIPrefix,SNOMED_CLASS_IRI));
      OWLClass snomedNewClass = factory.getOWLClass(iriOfNewClass);
      OWLSubClassOfAxiom subClassOfAxiom = factory.getOWLSubClassOfAxiom(snomedNewClass,diseaseClass);
      OWLAnnotationAssertionAxiom rdfsLabel = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(RDFS_LABEL), iriOfNewClass, factory.getOWLLiteral(targetOntoClassesToLabels.get(unmatchedClass),"en"));
      OWLAnnotationAssertionAxiom prefLabel = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(SNOMED_LABEL_PREFERED), iriOfNewClass, factory.getOWLLiteral(targetOntoClassesToLabels.get(unmatchedClass),"en"));
      if (classesToDefinition.containsKey(unmatchedClass)) {
        OWLAnnotationAssertionAxiom textDefinition = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(SNOMED_TEXT_DEFINITION), iriOfNewClass, factory.getOWLLiteral(classesToDefinition.get(unmatchedClass)));
        extendedSnomedManager.addAxiom(extendedSnomed,textDefinition);
      }
      extendedSnomedManager.addAxiom(extendedSnomed,subClassOfAxiom);
      extendedSnomedManager.addAxiom(extendedSnomed,rdfsLabel);
      extendedSnomedManager.addAxiom(extendedSnomed,prefLabel);
    }
  }

  private void addNewLabels(Map<OWLClass, Set<OWLClass>> targetToBaseMatches) {
    long newTextDefinition = 0, classSynonyms = 0;
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    for (OWLClass classInDoidOnto : targetToBaseMatches.keySet()) {
      for (OWLClass classInSnomed : targetToBaseMatches.get(classInDoidOnto)) {
        Set<OWLAnnotation> textDefAnnotation = EntitySearcher.getAnnotations(classInSnomed,snomedOntology,factory.getOWLAnnotationProperty(SNOMED_TEXT_DEFINITION)).collect(Collectors.toSet());
        if (textDefAnnotation.isEmpty()) {
          if (classesToDefinition.containsKey(classInDoidOnto)) {
            OWLAnnotationAssertionAxiom textDefinition = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(SNOMED_TEXT_DEFINITION), classInSnomed.getIRI(), factory.getOWLLiteral(classesToDefinition.get(classInDoidOnto)));
            extendedSnomedManager.addAxiom(extendedSnomed, textDefinition);
            newTextDefinition++;
          }
        }
        Set<String> classSynonymsAndPrefLabelsLowerCase = new HashSet<>();
        Set<OWLAnnotation> synonymsAndPrefLabelsOfClass = EntitySearcher.getAnnotations(classInSnomed,snomedOntology,factory.getOWLAnnotationProperty(SNOMED_LABEL_SYNONYM)).collect(Collectors.toSet());
        synonymsAndPrefLabelsOfClass.addAll(EntitySearcher.getAnnotations(classInSnomed,snomedOntology,factory.getOWLAnnotationProperty(SNOMED_LABEL_PREFERED)).collect(Collectors.toSet()));
        for (OWLAnnotation owlAnnot : synonymsAndPrefLabelsOfClass) {
          classSynonymsAndPrefLabelsLowerCase.add(owlAnnot.getValue().asLiteral().get().getLiteral().toLowerCase());
        }
        if (!classesToSynonymsLowerCase.containsKey(classInDoidOnto))
          continue;
        for (String synonymInDoid : classesToSynonymsLowerCase.get(classInDoidOnto)) {
          if (!classSynonymsAndPrefLabelsLowerCase.contains(synonymInDoid)) {
            OWLAnnotationAssertionAxiom synonymAnnotation = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(SNOMED_LABEL_SYNONYM), classInSnomed.getIRI(), factory.getOWLLiteral(synonymInDoid));
            extendedSnomedManager.addAxiom(extendedSnomed,synonymAnnotation);
            classSynonyms++;
          }
        }
      }
    }
    System.out.println(newTextDefinition + " " + classSynonyms);
  }
}