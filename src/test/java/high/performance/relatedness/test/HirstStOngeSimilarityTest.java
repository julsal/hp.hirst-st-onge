package high.performance.relatedness.test;

import high.performance.relatedness.Generator;
import high.performance.relatedness.HirstStOngeSimilarity;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.Word;
import net.didion.jwnl.dictionary.Dictionary;

import org.insight_centre.nlp.semantics.commons.syntax.StanfordLemmatizer;

import edu.cmu.lti.jawjaw.util.WordNetUtil;
import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.lexical_db.data.Concept;
import edu.cmu.lti.ws4j.Relatedness;
import edu.cmu.lti.ws4j.impl.HirstStOnge;

public class HirstStOngeSimilarityTest {

	static ILexicalDatabase db = new NictWordNet();

	static HirstStOnge ws4j = new HirstStOnge(db);

	private static Map<String, List<Concept>> conceptsCache = Collections
			.synchronizedMap(new HashMap<String, List<Concept>>());

	private static Map<String, Double> relatedness = Collections.synchronizedMap(new HashMap<String, Double>());

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws JWNLException, ClassNotFoundException, IOException {
		String wordnetPropertyFile = "/Users/juliano/Copy/workspace/WordNetDistributionalVector/lib/file_properties.xml";
		JWNL.initialize(new FileInputStream(wordnetPropertyFile));

		HirstStOngeSimilarity hsosim = Generator.restore("/Users/juliano/Desktop/sand/hso-data");

		Iterator<Synset> itera = Dictionary.getInstance().getSynsetIterator(POS.NOUN);
		Iterator<Synset> iterb;
		Synset a, b;
		while (itera.hasNext()) {
			a = itera.next();

			iterb = Dictionary.getInstance().getSynsetIterator(POS.NOUN);
			while (iterb.hasNext()) {
				b = iterb.next();

				for (Word wa : a.getWords()) {
					for (Word wb : b.getWords()) {
						float rela = getRelatedness(wa.getLemma(), wb.getLemma()).floatValue();
						float relb = hsosim.getRelatedness(wa.getLemma(), wb.getLemma());
						System.out.print(wa.getLemma() + " --- " + wb.getLemma());
						if (rela == relb) {
							System.out.println(" - OK: " + rela);
						} else {
							System.out.print(" - ERROR: diff(" + (rela - relb) + ") - ");
							System.out.print("WS4J: " + rela);
							System.out.println(" -- HSOSim: " + relb);
						}
					}
				}
			}

		}
	}

	// TODO improve performance...
	public synchronized static Double getRelatedness(String w1, String w2) {
		Relatedness result;

		Double rldns = null;

		rldns = relatedness.get(w1 + "-" + w2);
		if (rldns == null) {

			List<Concept> candidateConcepts = getConcepts(w1.trim(), null, null);
			List<Concept> sentConcepts = getConcepts(w2.trim(), null, null);

			if (sentConcepts != null && candidateConcepts != null) {

				for (Concept c1 : sentConcepts) {
					for (Concept c2 : candidateConcepts) {
						result = ws4j.calcRelatednessOfSynset(c1, c2);
						rldns = Math.max((rldns == null ? -1 : rldns), result.getScore());
					}
				}
			}

			relatedness.put(w1 + "-" + w2, rldns);
		}

		return rldns;
	}

	private static synchronized List<Concept> getConcepts(String word, String lemma, edu.cmu.lti.jawjaw.pobj.POS pos) {
		List<Concept> concepts;

		pos = edu.cmu.lti.jawjaw.pobj.POS.valueOf("n");
		if ((concepts = conceptsCache.get(word)) == null) {

			List<edu.cmu.lti.jawjaw.pobj.Synset> synsets = WordNetUtil.wordToSynsets(word, pos);
			if (synsets.isEmpty()) {
				synsets = WordNetUtil.wordToSynsets(
						(lemma != null ? lemma : StanfordLemmatizer.lemmatize(word).trim()), pos);
			}

			concepts = new ArrayList<Concept>(synsets.size());

			for (edu.cmu.lti.jawjaw.pobj.Synset synset : synsets) {
				concepts.add(new Concept(synset.getSynset(), pos));
			}

			conceptsCache.put(word, concepts);
		}

		return (!concepts.isEmpty() ? concepts : null);
	}
}
