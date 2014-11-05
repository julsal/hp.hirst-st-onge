package high.performance.relatedness;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HirstStOngeSimilarity implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final float EXTRA_STRONG = 16;

	public static final float STRONG = 16;

	public static final float HSO_CONSTANT_C = 8;

	public static final float HSO_CONSTANT_K = 1;

	protected Map<String, Set<String>> shareSynset = new HashMap<>();

	protected Map<String, Map<String, Set<String>>> directLinked = new HashMap<>();

	protected Map<String, Map<String, Float>> mediumStringRelations = new HashMap<>();

	public float getRelatedness(String word1, String word2) {
		if (word1 != null && word2 != null) {

			if (word1.equals(word2))

				return EXTRA_STRONG;

			else if ((shareSynset.containsKey(word1) && shareSynset.get(word1).contains(word2))
					|| (shareSynset.containsKey(word2) && shareSynset.get(word2).contains(word1))) {

				return STRONG;

			} else if ((directLinked.containsKey(word1) && directLinked.get(word1).containsKey(word2))
					|| (directLinked.containsKey(word2) && directLinked.get(word2).containsKey(word1))) {

				return STRONG;

			} else {
				Float relatedness = (mediumStringRelations.get(word1) != null ? mediumStringRelations.get(word1).get(
						word2) : null);
				if (relatedness == null) {
					relatedness = (mediumStringRelations.get(word2) != null ? mediumStringRelations.get(word2).get(
							word1) : null);
				}

				return (relatedness != null ? relatedness : 0);
			}

		} else
			throw new NullPointerException("No word can be null.");

	}

	public Map<String, Set<String>> getShareSynset() {
		return shareSynset;
	}

	public void setShareSynset(Map<String, Set<String>> shareSynset) {
		this.shareSynset = shareSynset;
	}

	public Map<String, Map<String, Set<String>>> getDirectLinked() {
		return directLinked;
	}

	public void setDirectLinked(Map<String, Map<String, Set<String>>> directLinked) {
		this.directLinked = directLinked;
	}

	public Map<String, Map<String, Float>> getMediumStringRelations() {
		return mediumStringRelations;
	}

	public void setMediumStringRelations(Map<String, Map<String, Float>> mediumStringRelations) {
		this.mediumStringRelations = mediumStringRelations;
	}
}
