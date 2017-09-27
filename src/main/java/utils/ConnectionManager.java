package utils;

import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public class ConnectionManager {
	
	private static SPARQLRepository repo;
	private static RepositoryConnection conn = null;
	
//	String endPointWithAlignments = "http://192.168.6.28:9108/repositories/clinicalKnowledge";
	private static String otherGraphDB = "http://beauty:18779/repositories/clinicalKnowledge";
	private static String sparqlEndpoint = "http://dev-ai-kb-external.babylontech.co.uk:7200/repositories/clinicalKnowledge";
	
	public ConnectionManager(String kbIRI) {
		repo = new SPARQLRepository(kbIRI);
		repo.initialize();
		repo.setUsernameAndPassword("admin", "babyKB&1");
		conn = repo.getConnection();		
	}
	
	public ConnectionManager() {
		repo = new SPARQLRepository(sparqlEndpoint);
		repo.initialize();
		repo.setUsernameAndPassword("admin", "babyKB&1");
		conn = repo.getConnection();
	}
	
	public void shutDown() {
		conn.close();
		repo.shutDown();
	}
	
	public TupleQuery prepareTupleQuery(String queryString) {
		return conn.prepareTupleQuery(queryString);
	}

}
