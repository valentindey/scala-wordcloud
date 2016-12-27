package wordcloud.pos

import wordcloud.utils.corpus.CorpusReader

@SerialVersionUID(123L)
abstract class PosTagger extends Serializable () {

  var tagCounts : Map[String, Int] = Map().withDefaultValue(0)  // tag -> count
  var seenTags : Map[String, Set[String]] = Map().withDefaultValue(Set())  // word -> possible tags
  var weights : Map[String, Array[Float]] = Map()  // tag -> weight vector

  def defaultTag: String

  def getFeatureVec(prevWord: String, prevTag: String, word: String, train: Boolean) : Array[Float]

  def train(corpus: CorpusReader): Unit = {

    for (sentence <- corpus.readSentences()) {
      var prevToken = BosToken
      for (token <- sentence) {
        seenTags += token.form -> (seenTags(token.form) + token.pos)
        tagCounts += token.pos -> (tagCounts(token.pos) + 1)
        val fVec = getFeatureVec(prevToken.form, prevToken.pos, token.form, train=true)
        if (weights.contains(token.pos)) {
          weights += token.pos -> weights(token.pos).zip(fVec).map{case (x, y) => x+y}
        }
        else {
          weights += token.pos -> fVec
        }
        prevToken = token
      }
    }
    // normalize weights, i.e. for tag in weights by dividing the corresponding weights
    // by the tags absolute frequency element wise
    for (tag <- weights.keys) {
      weights += tag -> weights(tag).zip(Stream.continually(tagCounts(tag))).map{case (x, y) => x/y}
    }
  }

  def tag(words : Array[String]): Array[String] = {

    var tags = Array("")

    var prevWord = ""
    var prevTag = BOS
    for (word <- words) {
      val possibleTags = seenTags.getOrElse(word, tagCounts.keys)
      var bestTag = defaultTag
      if (possibleTags.size == 1) {
        bestTag = possibleTags.head
      }
      else {
        val featVec = getFeatureVec(prevWord, prevTag, word, train=false)
        var maxSim = Float.NegativeInfinity
        for (pTag <- possibleTags) {
          val sim = featVec.zip(weights(pTag)).map{case (x, y) => x+y}.sum
          if (sim > maxSim) {
            maxSim = sim
            bestTag = pTag
          }
        }
      }
      prevWord = word
      prevTag = bestTag
      tags = tags :+ bestTag
    }
    tags.tail
  }
}