import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;

public class tfidfCalculator {

	public static void main(String args[]) throws Exception
	{
		//Get search query from user
    	System.out.print("TASK 2");
   	
	}
	
	 static HashMap<String, Float> computeIDF(Set<String> myterms)
			throws  IOException, ParseException {	 
			   	
		 		Similarity s = new ClassicSimilarity();
		    	//Hashmap to store term - score respectively
				HashMap<String,Float> score=new HashMap<String,Float>();
				
				IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get("C:\\Nikita\\Sem1\\Search\\Assignment2\\index\\index")));
				IndexSearcher searcher = new IndexSearcher(reader);
			
				// Get the preprocessed query terms
				Analyzer analyzer = new StandardAnalyzer();
				searcher.setSimilarity(s);
				
				//QueryParser parser = new QueryParser("TITLE", analyzer);
				//Query query = parser.parse(QueryParser.escape(queryString));

				//Set<Term> queryTerms = new LinkedHashSet<Term>();
				//searcher.createNormalizedWeight(query, false).extractTerms(queryTerms);	
				
				//for each term in a query
				for (String t : myterms)
				{	
	
					float idf=0;
					
				    	//Calcualte k(t)
				    	int kt=reader.docFreq(new Term("TEXT", t));
				    	
				    	//if term not present in that document skip rest and move to next term
				    	if (kt == 0) 
				    	{
				      	continue;
				    	}
				    	
				    	//Calculate N
				    	int totalno_docs=reader.maxDoc();		    	
						
						//calculate tf idf
						idf=(float) Math.log(1+(float)(totalno_docs/kt));
									
					    score.put(String.valueOf(t), idf);
				}
				return score;
		 }	 
	 }
