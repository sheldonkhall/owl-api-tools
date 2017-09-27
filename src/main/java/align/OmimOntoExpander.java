package align;

import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

/**
 * Created by giorgos.stoilos on 27/09/2017.
 */
public class OmimOntoExpander extends GeneralExpander {
  public OmimOntoExpander(String snomedDocumentFile) throws OWLOntologyCreationException {
    super(snomedDocumentFile);
  }

  @Override
  public void expandSnomed() throws OWLOntologyCreationException, OWLOntologyStorageException {

  }

  public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
    String path = "/Users/giorgos.stoilos/Documents/Ontologies/";
    String sourceOnto = path + "mine/omim/OMIM.ttl";;

    //Comparing using two .owl files
//    String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/SnomedCT_InternationalRF2_Production_20170131T120000/snomedct_owl.owl";
//    String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/extSnomed-doid.owl";
    String baseOntology = "/Users/giorgos.stoilos/Documents/Ontologies/extSnomed-symp-doid.owl";

    OmimOntoExpander omimExpander = new OmimOntoExpander(baseOntology);
    omimExpander.compareOntologiesFromFiles(sourceOnto);
    omimExpander.printResult();

//    omimExpander.expandSnomed();

  }

}
