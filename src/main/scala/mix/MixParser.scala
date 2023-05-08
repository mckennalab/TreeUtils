package main.scala.mix

import main.scala.node.NodeLinker

import scala.io.Source
import scala.util.matching.Regex


/**
  * Parse the output from the PHYLIP package MIX and create
  * event strings for each of the internal nodes
  */
class MixParser(mixOutput: String, eventCount: Int, treeToUse: Int, rootName: String, parsedTreeNodeCount: Int) {

  // the header line we're looking for in the file is:
  val headerLine = "From    To     Any Steps?    State at upper node"

  val inputFile = Source.fromFile(mixOutput).getLines().toList
  val headerLocations = inputFile.zipWithIndex.
    filter { case (line, index) => line contains headerLine }.
    map { case (ln, in) => in }

  val genotypeBlocks = headerLocations
  var inGenotypeSection = false
  var currentGenotype: Option[Edge] = None
  var currentTreeNumber = 0



  val extractor_sizes = ((eventCount - 1) / 5).floor.toInt
  val extractor_remainder = ((eventCount - 1) % 5)
  println(":ASDASDSD " + extractor_sizes)
  println(extractor_remainder)
  println(eventCount)
  // TODO: changing this to {1,8} broke the test
  val extractor_regex: Regex = ("\\s*([\\w\\d]{1,8})\\s+([\\w\\d]{1,8})\\s+([\\w\\d]{1,8})\\s+((?:[.01]{5}\\s+){" + extractor_sizes + "}(?:[.01]{" + extractor_remainder + "}))").r

  val splits = splitList(inputFile, headerLine).map { mk => mk.mkString("") }

  val parsed_trees = splits.map(split => {
    var activeTree = new NodeLinker()
    for (patternMatch <- extractor_regex.findAllMatchIn(split)) {
      val alleles = patternMatch.group(4).replace(" ", "")
      println(s"from : ${patternMatch.group(1)} to: ${patternMatch.group(2)} mod: ${patternMatch.group(3)} alleles ${alleles}")
      val edge = new Edge(patternMatch.group(1), patternMatch.group(2), rootName)
      edge.addChars(alleles)
      println("Fancy " + edge.toFancyString)
      activeTree.addEdge(edge)
    }
    activeTree
  }).toList

  val valid_trees = parsed_trees.filter{case(pt) => {
    pt.edgeCount() + 1 == parsedTreeNodeCount || // if we have a specified root node, standard relationship between edges and nodes
      pt.edgeCount() == parsedTreeNodeCount // edges = node count if we're not given a root node
  } }
  assert(valid_trees.size > 0)
  assert(treeToUse <= valid_trees.size)
  var activeTree = valid_trees(treeToUse)


  def clean_block(textBlock: List[String]): List[String] = {
    textBlock.filter(ln => ln.replaceAll(" ", "") != "")
  }

  /**
    * split a list of strings into piles based on
    *
    * @param list
    * @param delimiter
    * @return
    */
  def splitList(list: List[String], delimiter: String): List[List[String]] = {
    if (list.isEmpty) {
      List()
    } else {
      val (head, tail) = list.span(_ != delimiter)
      clean_block(head) :: splitList(tail.drop(1), delimiter)
    }
  }

}



