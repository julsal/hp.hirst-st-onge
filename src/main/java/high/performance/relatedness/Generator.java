package high.performance.relatedness;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Pointer;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.Word;
import net.didion.jwnl.dictionary.Dictionary;

public class Generator {

	public final String VERSION = "14.11.5-1";

	public static final int PATH_MIN_LEN = 2;

	public static final int PATH_MAX_LEN = 5;

	public static final Map<Direction, Set<String>> LINKS_KEY = new HashMap<>();

	static {

		Set<String> set = new HashSet<>();
		set.add(PointerType.ANTONYM.getKey());
		set.add(PointerType.SIMILAR_TO.getKey());
		set.add(PointerType.SEE_ALSO.getKey());
		LINKS_KEY.put(Direction.HORIZONTAL, set);

		set = new HashSet<>();
		set.add(PointerType.HYPERNYM.getKey());
		set.add(PointerType.MEMBER_MERONYM.getKey());
		set.add(PointerType.PART_MERONYM.getKey());
		set.add(PointerType.SUBSTANCE_MERONYM.getKey());
		LINKS_KEY.put(Direction.UPWARD, set);

		set = new HashSet<>();
		set.add(PointerType.CAUSE.getKey());
		set.add(PointerType.ENTAILED_BY.getKey());// ?
		set.add(PointerType.ENTAILMENT.getKey()); // ?
		set.add(PointerType.HYPONYM.getKey());
		set.add(PointerType.MEMBER_HOLONYM.getKey());
		set.add(PointerType.PART_HOLONYM.getKey());
		set.add(PointerType.SUBSTANCE_HOLONYM.getKey());
		LINKS_KEY.put(Direction.DOWNWARD, set);
	}

	// pattern and number of changes of direction
	private Map<Direction[], Integer> fullCombination;

	protected HirstStOngeSimilarity instance;

	public Generator() {
		this.instance = new HirstStOngeSimilarity();

		List<Direction[]> directionPatterns = new ArrayList<>(8);

		Direction[] pattern = { Direction.UPWARD };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.UPWARD, Direction.DOWNWARD };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.UPWARD, Direction.HORIZONTAL };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.UPWARD, Direction.HORIZONTAL, Direction.DOWNWARD };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.DOWNWARD };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.DOWNWARD, Direction.HORIZONTAL };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.HORIZONTAL, Direction.DOWNWARD };
		directionPatterns.add(pattern);

		pattern = new Direction[] { Direction.HORIZONTAL };
		directionPatterns.add(pattern);

		fullCombination = generateFullDirectionCombinationList(directionPatterns, PATH_MIN_LEN, PATH_MAX_LEN);
		fullCombination = Collections.unmodifiableMap(fullCombination);
		// System.out.println(fullCombination.size());
	}

	public void generateStaticData(String file) {
		try {
			JWNL.initialize(new FileInputStream(file));
			generateShareSynsetMap();
			generateMediumStrongRelationMap();
		} catch (JWNLException | FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	private void generateShareSynsetMap() throws JWNLException {
		Map<String, Set<String>> shareSynset = new HashMap<>();
		Map<String, Map<String, Set<String>>> directLinked = new HashMap<>();

		instance.setShareSynset(shareSynset);
		instance.setDirectLinked(directLinked);

		@SuppressWarnings("unchecked")
		Iterator<Synset> iter = Dictionary.getInstance().getSynsetIterator(POS.NOUN);

		Synset synset;
		while (iter.hasNext()) {
			synset = iter.next();

			Word[] words = synset.getWords();
			String word1;
			boolean approved;
			for (int i = 0; i < words.length; i++) {
				word1 = words[i].getLemma();

				Set<String> related = shareSynset.get(word1);
				if (related == null) {
					related = new HashSet<>();
					shareSynset.put(word1, related);
				}

				if ("entity".equals(word1))
					System.out.println("aqui");

				for (int j = i + 1; j < words.length; j++) {
					related.add(words[j].getLemma());
				}
			}

			Pointer[] pointers = synset.getPointers();
			String pointerType;

			for (Pointer p : pointers) {
				pointerType = p.getType().getKey();

				if (LINKS_KEY.get(Direction.HORIZONTAL).contains(pointerType)
						|| LINKS_KEY.get(Direction.DOWNWARD).contains(pointerType)
						|| LINKS_KEY.get(Direction.UPWARD).contains(pointerType)) {
					approved = LINKS_KEY.get(Direction.HORIZONTAL).contains(pointerType);

					for (Word mw : synset.getWords()) {
						word1 = mw.getLemma();
						Map<String, Set<String>> map = directLinked.get(word1);
						if (map == null) {
							map = new HashMap<>();
							directLinked.put(word1, map);
						}

						Synset tsynset = p.getTargetSynset();
						for (Word w : tsynset.getWords()) {
							String word2 = w.getLemma();

							if (approved || word1.indexOf(word2) != -1 || word2.indexOf(word1) != -1) {
								Set<String> set = map.get(word2);
								if (set == null) {
									set = new HashSet<>();
									map.put(word2, set);
								}

								// it will be used to filter antonym, when applied to adjective
								set.add(pointerType);
							}
						}
					}
				}
			}
		}
	}

	private void generateMediumStrongRelationMap() throws JWNLException {

		Map<String, Map<String, Float>> mediumStringRelations = new HashMap<>();
		instance.setMediumStringRelations(mediumStringRelations);

		int changes, index;
		float weight;
		Direction dir;
		Stack<Synset> sStack = new Stack<>();
		Stack<Integer> iStack = new Stack<>();
		for (Direction[] pattern : fullCombination.keySet()) {
			System.out.println("Evaluating pattern " + Arrays.toString(pattern));
			changes = fullCombination.get(pattern);

			weight = HirstStOngeSimilarity.HSO_CONSTANT_C - pattern.length
					- (HirstStOngeSimilarity.HSO_CONSTANT_K * changes);

			@SuppressWarnings("unchecked")
			Iterator<Synset> iter = Dictionary.getInstance().getSynsetIterator(POS.NOUN);

			Synset synset;
			Word[] baseWords;
			Float relatedness;
			while (iter.hasNext()) {
				synset = iter.next();
				baseWords = synset.getWords();

				// System.out.println("\nSYNSET BASE: " + synset.getKey() + " - Pattern: " + Arrays.toString(pattern));

				Pointer pointer;
				Pointer[] pointers;

				sStack.push(synset);
				iStack.push(0);
				index = 0;

				while (!sStack.isEmpty()) {
					pointers = sStack.pop().getPointers();
					dir = pattern[index];
					int i = iStack.pop();

					for (; i < pointers.length; i++) {

						pointer = pointers[i];

						if (LINKS_KEY.get(dir).contains(pointer.getType().getKey())) {
							if (index == pattern.length - 1) {
								for (Word baseWord : baseWords) {

									Map<String, Float> rels = mediumStringRelations.get(baseWord.getLemma());
									if (rels == null) {
										rels = new HashMap<>();
										mediumStringRelations.put(baseWord.getLemma(), rels);
									}

									for (Word targetWord : pointer.getTargetSynset().getWords()) {
										relatedness = rels.get(targetWord.getLemma());
										relatedness = (relatedness == null ? weight : Math.max(relatedness, weight));
										rels.put(targetWord.getLemma(), relatedness);
									}
								}

							} else if (!sStack.contains(synset)) {
								sStack.push(synset);
								iStack.push(i + 1);
								index++;

								pointers = pointer.getTargetSynset().getPointers();
								dir = pattern[index];
								i = -1;// it will became 0, when 'for' is executed.
							}
						}
					}

					index--;
				}
			}

		}

	}

	public static Map<Direction[], Integer> generateFullDirectionCombinationList(List<Direction[]> directionPatterns,
			final int MIN, final int MAX) {

		Map<Direction[], Integer> fullCombination = new HashMap<>();

		int[] counters;
		int sum;
		Direction[] current;
		for (Direction[] pattern : directionPatterns) {
			counters = new int[pattern.length];
			Arrays.fill(counters, 1);

			counters[0] = 0;

			while ((sum = next(counters, MIN, MAX)) != -1) {

				int i = 0;
				current = new Direction[sum];

				for (int c = 0; c < counters.length; c++)
					for (int j = 0; j < counters[c]; j++)
						current[i++] = pattern[c];

				// System.out.println(Arrays.toString(current) + " - " + (pattern.length - 1));
				fullCombination.put(current, (pattern.length));
			}
		}

		return (!fullCombination.isEmpty() ? fullCombination : null);
	}

	public static int next(int[] counters, final int MIN, final int MAX) {

		int len = counters.length;
		int base = 0;
		boolean go;
		int sum;
		do {
			go = false;
			counters[base]++;

			for (int i = 0; i < base; i++)
				counters[i] = 1;

			sum = sum(counters);
			if (sum > MAX) {
				base++;
				go = true;
			} else if (sum < MIN)
				go = true;

		} while (base < len && go);

		return (base < len ? sum : -1);
	}

	public static int sum(int[] array) {
		int total = 0;
		for (int i : array)
			total += i;

		return total;
	}

	public void persit(String filepath) throws FileNotFoundException, IOException {
		File file = new File(filepath);
		if (file.exists())
			file.delete();

		file.createNewFile();
		ObjectOutputStream ostream = new ObjectOutputStream(new FileOutputStream(file));
		ostream.writeObject(instance);
		ostream.close();

	}

	public static HirstStOngeSimilarity restore(String file) throws FileNotFoundException, IOException,
			ClassNotFoundException {
		ObjectInputStream ins = new ObjectInputStream(new FileInputStream(new File(file)));
		HirstStOngeSimilarity hso = (HirstStOngeSimilarity) ins.readObject();
		ins.close();

		return hso;
	}

	public static void main(String[] args) {
		args = new String[2];
		args[0] = "/Users/juliano/Copy/workspace/WordNetDistributionalVector/lib/file_properties.xml";
		args[1] = "/Users/juliano/Desktop/sand/static-hso";

		if (args != null && args.length == 2) {
			try {
				Generator generator = new Generator();
				System.out.println("Version " + generator.VERSION);

				generator.generateStaticData(args[0]);
				generator.persit(args[1]);

			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println(new Generator().VERSION);
			System.out.println("GENERATION USAGE: main <wordnet property file> <persistence destinary>");
		}

	}
}
