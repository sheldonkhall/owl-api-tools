package tools;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello World!"); // Display the string.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        IRI omimIri = IRI.create(new File("/Users/sheldon.hall/Projects/KB2.0/logmap2/OMIM.ttl"));
        try {
            OWLOntology o = m.loadOntology(omimIri);
            try {
                File output = File.createTempFile("OMIM", "owl", new File("."));
                IRI outputIri = IRI.create(output);
                try {
                    m.saveOntology(o, new OWLXMLOntologyFormat(), outputIri);
                } catch (OWLOntologyStorageException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
        System.out.println("Converted ontology.");
    }
}
