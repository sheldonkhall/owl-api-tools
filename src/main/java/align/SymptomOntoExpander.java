package align;

import com.google.common.collect.Sets;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by giorgos.stoilos on 25/09/2017.
 */
public class SymptomOntoExpander extends GeneralExpander {

  private String SYMPTOM_ONTO_ANCHOR_IN_SNOMED = "http://kb.babylonhealth.com/upper_level_ontology/STY/T184";


  public void expandSnomed() throws OWLOntologyCreationException, OWLOntologyStorageException {
    System.out.println("extending snomed using symptom ontology...");
    extendedSnomed = extendedSnomedManager.createOntology(snomedOntology.getOntologyID().getOntologyIRI().get());
    extendedSnomedManager.addAxioms(extendedSnomed,snomedOntology.getAxioms());

    //Create the Anchor for the new Classes
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    OWLClass symptomClass = factory.getOWLClass(IRI.create(SYMPTOM_ONTO_ANCHOR_IN_SNOMED));
    OWLSubClassOfAxiom symptomToFinding = factory.getOWLSubClassOfAxiom(symptomClass,factory.getOWLClass(IRI.create(SNOMED_CLASS_IRI + "404684003")));
    extendedSnomedManager.addAxiom(extendedSnomed,symptomToFinding);

    System.out.println("adding new classes");
    addNewConcepts(exactMatcher.getUnmatchedClasses());
    System.out.println("adding new semantic types");
    addNewSemanticTypes(exactMatcher.getNewOntoToSnomedMatches());
    System.out.println("adding new labels/definitions");
    addNewLabels(exactMatcher.getNewOntoToSnomedMatches());

    String pathToSave = "/Users/giorgos.stoilos/Documents/Ontologies/extSnomed.owl";
    File fileFormatted = new File(pathToSave);
    extendedSnomedManager.saveOntology(extendedSnomed, new OWLXMLDocumentFormat(), IRI.create(fileFormatted.toURI()));
  }

  private void addNewLabels(Map<OWLClass, Set<OWLClass>> targetToBaseMatches) {
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    for (OWLClass classInSymptomOnto : targetToBaseMatches.keySet()) {
      for (OWLClass classInSnomed : targetToBaseMatches.get(classInSymptomOnto)) {
        Set<OWLAnnotation> textDefAnnotation = EntitySearcher.getAnnotations(classInSnomed,snomedOntology,factory.getOWLAnnotationProperty(SNOMED_TEXT_DEFINITION)).collect(Collectors.toSet());
        if (textDefAnnotation.isEmpty()) {
          if (classesToDefinition.containsKey(classInSymptomOnto)) {
            OWLAnnotationAssertionAxiom textDefinition = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(SNOMED_TEXT_DEFINITION), classInSnomed.getIRI(), factory.getOWLLiteral(classesToDefinition.get(classInSymptomOnto)));
            extendedSnomedManager.addAxiom(extendedSnomed, textDefinition);
          }
        }
        Set<String> classSynonymsAndPrefLabelsLowerCase = new HashSet<>();
        Set<OWLAnnotation> synonymsAndPrefLabelsOfClass = EntitySearcher.getAnnotations(classInSnomed,snomedOntology,factory.getOWLAnnotationProperty(SNOMED_LABEL_SYNONYM)).collect(Collectors.toSet());
        synonymsAndPrefLabelsOfClass.addAll(EntitySearcher.getAnnotations(classInSnomed,snomedOntology,factory.getOWLAnnotationProperty(SNOMED_LABEL_PREFERED)).collect(Collectors.toSet()));
        for (OWLAnnotation owlAnnot : synonymsAndPrefLabelsOfClass) {
          classSynonymsAndPrefLabelsLowerCase.add(owlAnnot.getValue().asLiteral().get().getLiteral().toLowerCase());
        }
        if (!classesToSynonymsLowerCase.containsKey(classInSymptomOnto))
          continue;
        for (String synonymInSymp : classesToSynonymsLowerCase.get(classInSymptomOnto)) {
          if (!classSynonymsAndPrefLabelsLowerCase.contains(synonymInSymp)) {
            OWLAnnotationAssertionAxiom synonymAnnotation = factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(SNOMED_LABEL_SYNONYM), classInSnomed.getIRI(), factory.getOWLLiteral(synonymInSymp));
            extendedSnomedManager.addAxiom(extendedSnomed,synonymAnnotation);
          }
        }
      }
    }
  }

  private void addNewSemanticTypes(Map<OWLClass, Set<OWLClass>> targetToBaseMatches) {
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    OWLClass symptomClass = factory.getOWLClass(IRI.create(SYMPTOM_ONTO_ANCHOR_IN_SNOMED));

    for (Set<OWLClass> matchesInSnomedBatch : targetToBaseMatches.values())
      for (OWLClass classInSnomed : matchesInSnomedBatch)
        extendedSnomedManager.addAxiom(extendedSnomed,factory.getOWLSubClassOfAxiom(classInSnomed,symptomClass));

  }

  private void addNewConcepts(Set<OWLClass> unmatchedClasses) {
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    OWLClass symptomClass = factory.getOWLClass(IRI.create(SYMPTOM_ONTO_ANCHOR_IN_SNOMED));
    OWLDeclarationAxiom declarationAxiom = factory.getOWLDeclarationAxiom(symptomClass);
    extendedSnomedManager.addAxiom(extendedSnomed,declarationAxiom);

    OWLSubClassOfAxiom symptomToFinding = factory.getOWLSubClassOfAxiom(symptomClass,factory.getOWLClass(IRI.create(SNOMED_CLASS_IRI + "404684003")));
    extendedSnomedManager.addAxiom(extendedSnomed,symptomToFinding);

    for (OWLClass unmatchedClass : unmatchedClasses) {
      String oldIRIPrefix = unmatchedClass.getIRI().getIRIString();
      oldIRIPrefix = oldIRIPrefix.substring(0,oldIRIPrefix.lastIndexOf("/")+1);
      IRI iriOfNewClass =  IRI.create(unmatchedClass.getIRI().getIRIString().replace(oldIRIPrefix,SNOMED_CLASS_IRI));
      OWLClass snomedNewClass = factory.getOWLClass(iriOfNewClass);
      OWLSubClassOfAxiom subClassOfAxiom = factory.getOWLSubClassOfAxiom(snomedNewClass,symptomClass);
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

  public SymptomOntoExpander(String snomedDocumentFile) throws OWLOntologyCreationException {
    super(snomedDocumentFile);
  }

  public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
    String path = "/Users/sheldon.hall/Google Drive/KB2.0/logmap/";
    String sourceOnto = "symp.owl";

    //Comparing using two .owl files
    String baseOntology = "/Users/sheldon.hall/Projects/KB2.0/logmap2/snomedct_owl.owl";

    SymptomOntoExpander sympExpander = new SymptomOntoExpander(baseOntology);
    sympExpander.compareOntologiesFromFiles(path + sourceOnto);
    sympExpander.printResult();

    sympExpander.computeCoOccurrenceOfSuperTypes();
//    sympExpander.expandSnomed();

  }

//  @Override  private Map<OWLClass,Set<String>> classesToSynonymsLowerCase;
//  protected Map<OWLClass, String> collectTargetOntoLabels(OWLOntology newOnto) {
//
//    Map<OWLClass,String> classesToLabel = new HashMap<>();
//    //So far it is only taking only one text label, either the rdfs:label or skos:prefLabel, whatever it finds first.
//    String classLabel = null;
//    for (OWLClass classInNewOnto : newOnto.getClassesInSignature()) {
//      for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(classInNewOnto, newOnto).collect(Collectors.toSet())) {
//        if (a.getProperty().getIRI().getIRIString().equals("http://purl.obolibrary.org/obo/IAO_0000115"))
//          classesToDefinition.put(classInNewOnto,a.getValue().asLiteral().get().getLiteral());
//        if (a.getProperty().getIRI().getIRIString().equals("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")) {
//          Set<String> synonyms = classesToSynonyms.computeIfAbsent(classInNewOnto, k -> new HashSet<>());
//          Set<String> synonymsLowerCase = classesToSynonymsLowerCase.computeIfAbsent(classInNewOnto, k -> new HashSet<>());
//          synonyms.add(a.getValue().asLiteral().get().getLiteral());
//          synonymsLowerCase.add(a.getValue().asLiteral().get().getLiteral().toLowerCase());
//        }
//        if (!a.getProperty().toString().equals("rdfs:label") &&
//            !(a.getProperty().getIRI().equals(SKOSVocabulary.PREFLABEL.getIRI()) && ("en".equals(a.getValue().asLiteral().get().getLang()) ||
//                "eng".equals(a.getValue().asLiteral().get().getLang()))))
//          continue;
//        classLabel = a.getValue().asLiteral().get().getLiteral();
//      }
//      if (classLabel==null) {
//        //class did not have a label so I take the fragment of its IRI.
//        String iriBreak = "#";
//        if (!classInNewOnto.getIRI().getIRIString().contains("#"))
//          iriBreak = "/";
//        classLabel = classInNewOnto.getIRI().getIRIString().substring(classInNewOnto.getIRI().getIRIString().lastIndexOf(iriBreak)+1);
//      }
//      classesToLabel.put(classInNewOnto, classLabel.toLowerCase());
//    }
//    return classesToLabel;
//  }
}