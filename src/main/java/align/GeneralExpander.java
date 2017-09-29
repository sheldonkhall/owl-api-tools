package align;

import com.google.common.collect.Sets;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by giorgos.stoilos on 26/09/2017.
 */
public abstract class GeneralExpander {

  protected OWLOntology snomedOntology;
  protected Map<OWLClass,Set<String>> snomedClassesToMatchToLabels;
  protected Map<OWLClass,String> targetOntoClassesToLabels;

  protected OWLOntology newOnto;

  protected ExactMatcher exactMatcher;
  protected Map<OWLClass,String> classesToDefinition;
  protected Map<OWLClass,Set<String>> classesToSynonyms;
  protected Map<OWLClass,Set<String>> classesToSynonymsLowerCase;

  protected OWLOntologyManager extendedSnomedManager = OWLManager.createOWLOntologyManager();
  protected OWLOntology extendedSnomed;

  protected String SNOMED_CLASS_IRI = "http://snomed.info/id/";
  protected String SNOMED_TEXT_DEFINITION = "http://snomed.info/field/TextDefinition.term";
  protected String SNOMED_LABEL_PREFERED = "http://snomed.info/field/Description.term.en-us.preferred";
  protected String SNOMED_LABEL_SYNONYM = "http://snomed.info/field/Description.term.en-us.synonym";

  protected String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";

  public GeneralExpander(String snomedDocumentFile) throws OWLOntologyCreationException {
    exactMatcher = new ExactMatcher();
    loadSnomed(snomedDocumentFile);

    classesToDefinition = new HashMap<>();
    classesToSynonyms = new HashMap<>();
    classesToSynonymsLowerCase = new HashMap<>();
  }

  private void loadSnomed(String snomedDocumentFile) throws OWLOntologyCreationException {
    OWLOntologyManager sourceMan =  OWLManager.createOWLOntologyManager();
    snomedOntology = sourceMan.loadOntologyFromOntologyDocument(new File(snomedDocumentFile));

    //We may use Anchoring to Restrict the part of SNOMED for which we care about.
    Set<OWLClass> classesToIterateOver = new HashSet<>(snomedOntology.getClassesInSignature());
    snomedClassesToMatchToLabels = collectSnomedConceptLabels(classesToIterateOver,snomedOntology);
  }

  public void compareOntologiesFromFiles(String newOntology) throws OWLOntologyCreationException {
    OWLOntologyManager newOntoMan =  OWLManager.createOWLOntologyManager();
    newOnto = newOntoMan.loadOntologyFromOntologyDocument(new File(newOntology));
    targetOntoClassesToLabels = collectTargetOntoLabels(newOnto);

    System.out.println(targetOntoClassesToLabels.keySet().size());

    exactMatcher.compareSetsOfClasses(targetOntoClassesToLabels, snomedClassesToMatchToLabels);
  }

  protected Map<OWLClass, Set<String>> collectSnomedConceptLabels(Set<OWLClass> classesToIterateOver, OWLOntology baseOntology) {
    Map<OWLClass,Set<String>> classesToLabels = new HashMap<>();
    for (OWLClass baseClass : classesToIterateOver) {
      for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(baseClass, baseOntology).collect(Collectors.toSet())) {
        Set<String> labels = classesToLabels.computeIfAbsent(baseClass, k -> new HashSet<>());
        if (a.getValue().asLiteral().isPresent() && !a.getProperty().toString().contains("TextDefinition")) {
          labels.add(a.getValue().asLiteral().get().getLiteral().replaceAll(" \\(.*?\\)", ""));
        }
      }
    }
    return classesToLabels;
  }

  public abstract void expandSnomed() throws OWLOntologyCreationException, OWLOntologyStorageException;

  protected Map<OWLClass, String> collectTargetOntoLabels(OWLOntology newOnto) {

    Map<OWLClass,String> classesToLabel = new HashMap<>();
    //So far it is only taking only one text label, either the rdfs:label or skos:prefLabel, whatever it finds first.
    String classLabel = null;
    OWLDataFactory factory = extendedSnomedManager.getOWLDataFactory();
    for (OWLClass classInNewOnto : newOnto.getClassesInSignature()) {
      Set<OWLAnnotation> depricatedClassAnnotations = EntitySearcher.getAnnotations(classInNewOnto,newOnto,factory.getOWLAnnotationProperty("http://www.w3.org/2002/07/owl#deprecated")).collect(Collectors.toSet());
      if (!depricatedClassAnnotations.isEmpty())
        continue;
      for (OWLAnnotationAssertionAxiom a : EntitySearcher.getAnnotationAssertionAxioms(classInNewOnto, newOnto).collect(Collectors.toSet())) {
        if (a.getProperty().getIRI().getIRIString().equals("http://purl.obolibrary.org/obo/IAO_0000115"))
          classesToDefinition.put(classInNewOnto,a.getValue().asLiteral().get().getLiteral());
        if (a.getProperty().getIRI().getIRIString().equals("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")) {
          Set<String> synonyms = classesToSynonyms.computeIfAbsent(classInNewOnto, k -> new HashSet<>());
          Set<String> synonymsLowerCase = classesToSynonymsLowerCase.computeIfAbsent(classInNewOnto, k -> new HashSet<>());
          synonyms.add(a.getValue().asLiteral().get().getLiteral());
          synonymsLowerCase.add(a.getValue().asLiteral().get().getLiteral().toLowerCase());
        }
        if (!a.getProperty().toString().equals("rdfs:label") &&
            !(a.getProperty().getIRI().equals(SKOSVocabulary.PREFLABEL.getIRI()) && ("en".equals(a.getValue().asLiteral().get().getLang()) ||
                "eng".equals(a.getValue().asLiteral().get().getLang()))))
          continue;
        classLabel = a.getValue().asLiteral().get().getLiteral();
      }
      if (classLabel==null) {
        //class did not have a label so I take the fragment of its IRI.
        String iriBreak = "#";
        if (!classInNewOnto.getIRI().getIRIString().contains("#"))
          iriBreak = "/";
        classLabel = classInNewOnto.getIRI().getIRIString().substring(classInNewOnto.getIRI().getIRIString().lastIndexOf(iriBreak)+1);
      }
      classesToLabel.put(classInNewOnto, classLabel.toLowerCase());
    }
    return classesToLabel;
  }

  public void printResult() {
    System.out.println("Number of unmatched classes: " + exactMatcher.getUnmatchedClasses().size());
    exactMatcher.getUnmatchedClasses().forEach(e -> System.out.println(e.getIRI().getIRIString() + "\t" + targetOntoClassesToLabels.get(e)));

    System.out.println("Number of matched Classes: " + exactMatcher.getNewOntoToSnomedMatches().keySet().size());
    exactMatcher.getNewOntoToSnomedMatches().entrySet().forEach(e -> System.out.println(e.getKey().getIRI().getIRIString() + "\t" + targetOntoClassesToLabels.get(e.getKey()) + "\t" + e.getValue()));
  }

  protected void computeCoOccurrenceOfSuperTypes() {

    // build the map from new onto superclasses to snomed superclasses
    HashMap<OWLClass, List<OWLClass>> snomedToNewClassCounter = new HashMap<>();
    System.out.println("Number of matched Classes: " + this.exactMatcher.getNewOntoToSnomedMatches().keySet().size());
    this.exactMatcher.getNewOntoToSnomedMatches().forEach((key, value) -> {
      this.newOnto.getSubClassAxiomsForSubClass(key).forEach(owlSubClassOfAxiom -> {
        if (!owlSubClassOfAxiom.getSuperClass().isAnonymous()) {
          System.out.print("symp onto member = ");
          System.out.println(owlSubClassOfAxiom.getSuperClass());
          List<OWLClass> connectedClassList = snomedToNewClassCounter.getOrDefault(owlSubClassOfAxiom.getSuperClass().asOWLClass(), new ArrayList<>());
          value.forEach(match -> {
            System.out.print("snomed concept "+match.toString()+" has these superclasses: ");
            this.snomedOntology.getSubClassAxiomsForSubClass(match).forEach(matchSubClassAxiom -> {
              matchSubClassAxiom.getSuperClass().nestedClassExpressions().forEach(System.out::print);
              // deal with one level of nesting - reasoner is probably the correct way to do this
              matchSubClassAxiom.getSuperClass().nestedClassExpressions().forEach(nestedMatch -> {
                if (!nestedMatch.isAnonymous()) {
                  connectedClassList.add(nestedMatch.asOWLClass());
                }
              });
            });
            System.out.println(" end.");
          });
          snomedToNewClassCounter.put(owlSubClassOfAxiom.getSuperClass().asOWLClass(), connectedClassList);
        }
      });
    });

    System.out.println("Output class co-occurrence");
    snomedToNewClassCounter.forEach((key,value)->{
      System.out.print(key);
      System.out.print(" maps to ");
      System.out.println(value);
    });

    // build the co-occurence matrix
    int nRows = snomedToNewClassCounter.keySet().size();
    int nCols = snomedToNewClassCounter.values().stream().
            map(list-> Sets.newHashSet(list)).
            reduce(new HashSet(),(element, identity) -> {
              element.addAll(identity);
              return element;
            }).size();
    int[][] matrix = new int[nRows][nCols];
    // fill matrix
    List<OWLClass> rowMap = new ArrayList<>();
    List<OWLClass> colMap = new ArrayList<>();
    snomedToNewClassCounter.forEach((key,value)->{
      if (!rowMap.contains(key)) {rowMap.add(key);}
      value.forEach(snoMedClass->{
        if (!colMap.contains(snoMedClass)) {colMap.add(snoMedClass);}
        ++matrix[rowMap.indexOf(key)][colMap.indexOf(snoMedClass)];
      });
    });

    for (int i=0;i<nRows;i++) {
      for (int j=0;j<nCols;j++) {
        if (matrix[i][j] > 1) {
          System.out.print(rowMap.get(i).toString()+" + ");
          System.out.print(colMap.get(j).toString()+" with evidence = ");
          System.out.println(matrix[i][j]);
        }
      }
    }
  }

}
