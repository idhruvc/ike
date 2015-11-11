package org.allenai.dictionary

import org.allenai.common.Logging


/** Implement this trait for expanding (generalizing) tables with seed entries.
  * Various similarity measures may be used. Each can be implemented as a separate TableExpander.
  * Different similarity measures may potentially become features to train a classifier,
  * which would be implemented in turn, as another TableExpander.
  */
trait TableExpander {
  def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase]
}

/** Class that generalizes a given table (column) entries using Word2Vec. The basic idea here is:
  * expand each seed row in the given column using Word2Vec, then determine / return the
  * intersection set. Uses WordVecPhraseSearcher internally to expand each table entry.
  * @param word2vecSearcher
  */
class Word2VecTableExpander1(word2vecSearcher: WordVecPhraseSearcher)
  extends Logging with TableExpander {

  override def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase] = {
    // Get index of the required column in the table.
    val colIndex = table.getIndexOfColumn(columnName)

    // Construct set of all table rows. If the same entries appear in the similar phrases result
    // returned, we need to filter them out.
    val currentTableEntries = new scala.collection.mutable.HashSet[Seq[QWord]]()

    // Map containing each similar phrase result obtained with corresponding maximum similarity
    // score of this phrase with an existing table entry.
    val similarPhraseScoreMap = new scala.collection.mutable.HashMap[Seq[QWord], Double]()

    val candidateEntriesSets = for {
      row <- table.positive
    } yield {
      val tableEntry = row.values(colIndex)
      currentTableEntries.add(tableEntry.qwords)
      val similarPhrases = word2vecSearcher.getSimilarPhrases(
        tableEntry.qwords.map(_.value).mkString(" "))
      for (similarPhrase <- similarPhrases) {
        if (!similarPhraseScoreMap.contains(similarPhrase.qwords) ||
          (similarPhraseScoreMap(similarPhrase.qwords) < similarPhrase.similarity)) {
          similarPhraseScoreMap(similarPhrase.qwords) = similarPhrase.similarity
        }
      }
      similarPhrases.map(_.qwords).toSet
    }
    ((candidateEntriesSets.reduceLeft[Set[Seq[QWord]]] {
      (set1, set2) => set1 intersect set2
    } diff currentTableEntries.toSet).toSeq map (c =>
      new SimilarPhrase(c, similarPhraseScoreMap(c)))).sortBy(_.similarity).reverse
  }
}


/** Class that generalizes a given table (column) entries using Word2Vec. The basic idea here is:
  * get the word2vec centroid of all seed entries, then return the neighbors of the centroid.
  * @param word2vecSearcher
  */
class Word2VecTableExpander2(word2vecSearcher: WordVecPhraseSearcher)
  extends Logging with TableExpander {

  override def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase] = {
    // Get index of the required column in the table.
    val colIndex = table.getIndexOfColumn(columnName)

    // Construct set of all table rows. If the same entries appear in the similar phrases result
    // returned, we need to filter them out.
    val currentTableEntries = new scala.collection.mutable.HashSet[Seq[QWord]]()

    val columnEntries = for {
      row <- table.positive
    } yield {
        val tableEntry = row.values(colIndex)
        currentTableEntries.add(tableEntry.qwords)
        tableEntry.qwords.map(_.value).mkString(" ")
    }
    word2vecSearcher.getCentroidMatches(columnEntries) filter(
      x => !currentTableEntries.contains(x.qwords))
  }
}
