import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;

public class Rocchio {

	public static Analyzer analyzer = new StandardAnalyzer();
    public static IndexWriterConfig config = new IndexWriterConfig(analyzer);
    public static RAMDirectory ramDirectory = new RAMDirectory();
    public static IndexWriter indexWriter;
    
	public static void main(String[] args) throws IOException, ParseException {
		List<ArrayList<String>> allDoc = computeAllDocs();
		
		ArrayList<String> ListOfTitle = new ArrayList<String>();
		ArrayList<String> relevantCount = allDoc.get(4);
		ArrayList<String> nonRelevantCount = allDoc.get(5);
		
        IndexWriter writer = indexer();
        for(int i=0; i<allDoc.get(0).size(); i++) {
			Document lDoc = new Document();
			lDoc.add(new StringField("NUM", allDoc.get(0).get(i),Field.Store.YES));
			FieldType myFieldType = new FieldType(TextField.TYPE_STORED);
			myFieldType.setStoreTermVectors(true);
			lDoc.add(new Field("TITLE", allDoc.get(1).get(i), myFieldType));
			lDoc.add(new Field("RELEVANT", allDoc.get(2).get(i), myFieldType));
			lDoc.add(new Field("IRRELEVANT", allDoc.get(3).get(i), myFieldType));
			writer.addDocument(lDoc);
		}
        
        
		writer.close();
		
		String indexPath = "C:\\Nikita\\Sem1\\Search\\Assignment3\\Index";
		IndexReader idxReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
		
		//Setting alpha to 1
		//Playing with beta and gamma values between 0.2 and 1
        double alpha = 1, beta = 1, gamma = 0;
		
		for (int in = 0; in < 50; in++) {
			Set<String> myterms = new LinkedHashSet<String>(); 
		    Map<String, Integer> initQuery = computeTermFreq(idxReader, in, "TITLE", myterms);
			Map<String, Integer> relDocs = computeTermFreq(idxReader, in, "RELEVANT", myterms);
			Map<String, Integer> nonRelDocs = computeTermFreq(idxReader, in, "NONRELEVANT", myterms);
			int rc = Integer.parseInt(relevantCount.get(in));
			int nrc = Integer.parseInt(nonRelevantCount.get(in));
			//Creating vectors for q0, Dr and Dnr
			RealVector q0 = toRealVector(initQuery, myterms);
			RealVector Dr = toRealVector(relDocs, myterms);
			RealVector Dnr = toRealVector(nonRelDocs, myterms);
			
			RealVector qM;
			if(rc!=0) {
				qM  = (q0.mapMultiply(alpha)).add(Dr.mapMultiply(beta / rc)).subtract(Dnr.mapMultiply(gamma / nrc));
						
			}
			else {
				qM = (q0.mapMultiply(alpha)).subtract(Dnr.mapMultiply(gamma / nrc));
			}
					
			String newQuery = "";
			for (int i = 0; i < qM.getDimension(); i++) {
				if (qM.getEntry(i) > 0) {
					newQuery += new ArrayList<>(myterms).get(i) + " ";
				}
			}
			
			ListOfTitle.add(newQuery);
			
			HashMap<String,Float> idf=new HashMap<String,Float>();
			idf = tfidfCalculator.computeIDF(myterms);
			
			LinkedHashMap<String, Float> reverseSortedMap = new LinkedHashMap<>();
			 
			//https://stackoverflow.com/questions/30842966/how-to-sort-a-hash-map-using-key-descending-order
			//Use Comparator.reverseOrder() for reverse ordering
			idf.entrySet()
			    .stream()
			    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())) 
			    .limit(300)
			    .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));
			
			myterms= reverseSortedMap.keySet();
		
		}
		

		Similarity dsimi = new ClassicSimilarity();
				
		//Evaluation
		
		Similarity s1 = new ClassicSimilarity();	
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get("C:\\Nikita\\Sem1\\Search\\Assignment3\\RocchioFeedback\\index")));
		IndexSearcher searcher1 = new IndexSearcher(reader); 
		String TitleQuery = "C:\\Nikita\\Sem1\\Search\\Assignment3\\ResultsBeta_"+alpha+"Gamma_"+gamma+".txt"; 
		FileWriter fw=new FileWriter(TitleQuery);  
		String queryString;
		for(int in=0; in<50; in++) {
			queryString = ListOfTitle.get(in);
			compareAlgorithms(in, "short", queryString, s1, searcher1, fw);
		}
		fw.flush();
		fw.close();
		
		
	}
	public static List<ArrayList<String>> computeAllDocs() throws IOException {
		List<ArrayList<String>> allDoc = new ArrayList<ArrayList<String>>();
		File file = new File(".\topic judgement for feedback.txt");
		StringBuffer sb = new StringBuffer();
		
		//List of arrays to store Num,Title,Relevant,Irrelevant data
		ArrayList<String> ListOfNumTag = new ArrayList<String>(); 
		ArrayList<String> ListOfTitle = new ArrayList<String>(); 
		ArrayList<String> ListOfRelevant = new ArrayList<String>(); 
		ArrayList<String> ListNonRelevant = new ArrayList<String>(); 
		ArrayList<String> relCounts = new ArrayList<String>(); 
		ArrayList<String> nonRelCounts = new ArrayList<String>(); 

		if (file.isFile()) {
			try (BufferedReader br  = new BufferedReader(new FileReader(file))) {
				String strCurrentLine;
				Boolean start = false;
				Boolean stop = false;
				int rc=0,nrc=0;
				while ((strCurrentLine = br.readLine()) != null) {
					if(strCurrentLine.startsWith("<num>")) {
						start = true;
						stop = false;
					}
					if(strCurrentLine.startsWith("</top>")) {
						sb.append("</top>");
						stop = true;
						start = false;
					}
					if(start == true && stop != true) {
						sb.append(" " + strCurrentLine);
					}			
					if(strCurrentLine.startsWith("<relevant>"))
						rc++;
					if(strCurrentLine.startsWith("<irrelevant>"))
						nrc++;
					if(strCurrentLine.startsWith("</top>")) 
					{
						Matcher m = Pattern.compile("(?<=<num>).+?(?=<)", Pattern.DOTALL)
								.matcher(sb.toString());
						while (m.find()) {
							ListOfNumTag.add(m.group());
						}
						m = Pattern.compile("(?<=<title>).+?(?=<)", Pattern.DOTALL)
								.matcher(sb.toString());
						while (m.find()) {
							ListOfTitle.add(m.group());
						}
						if(sb.toString().contains("<relevant>")) {
							m = Pattern.compile("(?<=<relevant>).+?(?=<irrelevant>)", Pattern.DOTALL)
									.matcher(sb.toString());
							while (m.find()) {
									ListOfRelevant.add(m.group().replace("<relevant>", ""));
								}
						}
						else {
							ListOfRelevant.add("");
						}
						m = Pattern.compile("(?<=<irrelevant>).+?(?=</top>)", Pattern.DOTALL)
								.matcher(sb.toString());
						while (m.find()) {
							ListNonRelevant.add(m.group().replace("<irrelevant>", ""));
						}
						relCounts.add(Integer.toString(rc));
						nonRelCounts.add(Integer.toString(nrc));
						allDoc.add(ListOfNumTag);
						allDoc.add(ListOfTitle);
						allDoc.add(ListOfRelevant);
						allDoc.add(ListNonRelevant);
						allDoc.add(relCounts);
						allDoc.add(nonRelCounts);
						sb = new StringBuffer();
						rc=0;
						nrc=0;
					}
				}
			}
		}
		return allDoc;
	}
	
	private static IndexWriter indexer() {
		String indexPath = "C:\\Nikita\\Sem1\\Search\\Assignment3\\Index";
		
		try {
			
			System.out.println("Indexing to directory '" + indexPath + "'...");

			Directory dir = FSDirectory.open(Paths.get(indexPath));
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

			iwc.setOpenMode(OpenMode.CREATE);

			IndexWriter writer = new IndexWriter(dir, iwc);
			return writer;
		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
			return null;
		}
		
	}
	
	static Map<String, Integer> computeTermFreq(IndexReader idxReader, int docId, String content, Set<String> myterms) throws IOException {       
        Map<String, Integer> frequencies = new HashMap<>();
		
			//IndexSearcher idxSearcher = new IndexSearcher(idxReader);
			Terms terms = idxReader.getTermVector(docId, content); 
			if (terms != null) {
				TermsEnum termsEnum = terms.iterator();
				BytesRef bytesRef = termsEnum.next();
				while (bytesRef != null) {
					if (bytesRef != null) {
						String term = bytesRef.utf8ToString();
						int freq = (int) termsEnum.totalTermFreq();
						frequencies.put(term, freq);
						myterms.add(term);
					}
					bytesRef = termsEnum.next();
				} 
			}
		
		return frequencies;

    }

    static RealVector toRealVector(Map<String, Integer> map, Set<String> myterms) {
        RealVector vector = new ArrayRealVector(myterms.size());
        int i = 0;
        for (String term : myterms) {
            int value = map.containsKey(term) ? map.get(term) : 0;
            vector.setEntry(i++, value);
        }
        
        return vector;
    }
    
	public static void compareAlgorithms(int in, String type, String queryString, Similarity s1, IndexSearcher searcher1, FileWriter fw) throws ParseException, IOException
	{	

	    				int rank=1;
						BooleanQuery.setMaxClauseCount(50000);
    				
    					searcher1.setSimilarity(s1);
    					
    					Analyzer analyzer = new StandardAnalyzer();
    					QueryParser parser = new QueryParser("TEXT", analyzer);
    					Query query = parser.parse(QueryParser.escape(queryString));
    					
    					TopDocs topDocs = searcher1.search(query, 1000);
    					//get relevance score
    					ScoreDoc[] docs = topDocs.scoreDocs;
    					for (int i = 0; i < docs.length; i++) 
    					{
    						Document doc = searcher1.doc(docs[i].doc);
    						System.out.println(doc.get("DOCNO"));
    						fw.append(Integer.toString(in+51));
    						fw.append("\t" + "Q"+(in));
    						fw.append("\t" + doc.get("DOCNO"));
    						fw.append("\t" + rank);
    						fw.append("\t" + docs[i].score);
    						fw.append("\t" + "run-1\n");
     						//pw.write((docNumFromFile[1]+"\tQ"+(j-1)+"\t"+ doc.get("DOCNO") + "\t"+rank +"\t"+ docs[i].score+"\t"+"run-"+j+"\n"));
     						rank++;
    					}
    				}
    			}


