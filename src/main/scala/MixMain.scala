package main.scala

import java.io.{File, PrintWriter}

import main.scala.annotation.AnnotationsManager
import main.scala.cells.CellAnnotations
import main.scala.mix._
import main.scala.node.{BestTree, RichNode}


/**
  * created by aaronmck on 2/13/14
  *
  * Copyright (c) 2014, aaronmck
  * All rights reserved.
  *
  * Redistribution and use in source and binary forms, with or without
  * modification, are permitted provided that the following conditions are met:
  *
  * 1. Redistributions of source code must retain the above copyright notice, this
  * list of conditions and the following disclaimer.
  * 2.  Redistributions in binary form must reproduce the above copyright notice,
  * this list of conditions and the following disclaimer in the documentation
  * and/or other materials provided with the distribution.
  *
  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
  * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
  *
  */
case class MixConfig(allEventsFile: Option[File] = None,
                     outputTree: Option[File] = None,
                     mixRunLocation: Option[File] = None,
                     mixLocation: Option[File] = None,
                     allCellAnnotations: Option[File] = None,
                     sample: String = "UNKNOWN",
                     firstX: Int = -1)

/**
  * generate a GESTALT tree from editing information, create a final JSON tree
  */
object MixMain extends App {

  // parse the command line arguments
  val parser = new scopt.OptionParser[MixConfig]("MixMain") {
    head("MixToTree", "1.3")

    // *********************************** Inputs *******************************************************
    opt[File]("allEventsFile") required() valueName ("<file>") action { (x, c) => c.copy(allEventsFile = Some(x)) } text ("the input stats file")
    opt[File]("mixRunLocation") required() valueName ("<file>") action { (x, c) => c.copy(mixRunLocation = Some(x)) } text ("the tree to produce")
    opt[Int]("subsetFirstX") valueName ("<int>") action { (x, c) => c.copy(firstX = x) } text ("the tree to produce")
    opt[File]("outputTree") required() valueName ("<file>") action { (x, c) => c.copy(outputTree = Some(x)) } text ("the tree to produce")
    opt[File]("allCells") valueName ("<file>") action { (x, c) => c.copy(allCellAnnotations = Some(x)) } text ("the tree to produce")
    opt[String]("sample") required() valueName ("<file>") action { (x, c) => c.copy(sample = x) } text ("the tree to produce")
    opt[File]("mixEXEC") required() valueName ("<file>") action { (x, c) => c.copy(mixLocation = Some(x)) } text ("where to find mix")

    // some general command-line setup stuff
    note("process a stats file into a JSON tree file\n")
    help("help") text ("prints the usage information you see here")
  }

  // *********************************** Run *******************************************************
  parser.parse(args, MixConfig()) map { config => {

    MixRunner.mixLocation = config.mixLocation.get

    // parse the all events file into an object
    val readEventsObj = EventIO.readEventsObject(config.allEventsFile.get, config.sample)

    // load up any annotations we have
    val annotationMapping = new AnnotationsManager(readEventsObj)

    val rootNode = if (config.firstX > 0) {
      println("Running split-tree...")
      EventSplitter.splitInTwo(config.mixRunLocation.get,
        readEventsObj,
        4,
        config.sample,
        annotationMapping)
    } else {
      println("Running single tree...")
      val (rootNode, linker) = MixRunner.mixOutputToTree(
        MixRunner.runMix(config.mixRunLocation.get,readEventsObj), readEventsObj, annotationMapping, "root")
      // post-process the final tree
      MixRunner.postProcessTree(rootNode, linker, readEventsObj, annotationMapping)
    }

    // ------------------------------------------------------------
    // If asked, add cells to the leaf nodes
    // ------------------------------------------------------------
    if (config.allCellAnnotations.isDefined) {
      val childAnnot = new CellAnnotations(config.allCellAnnotations.get)
      RichNode.addCells(rootNode, childAnnot, "white")
      childAnnot.printUnmatchedCells()

      rootNode.resetChildrenAnnotations()
    }

    // ------------------------------------------------------------
    // traverse the nodes and add names to any internal nodes without names
    // ------------------------------------------------------------

    // get an updated height to flip the tree around
    val maxHeight = RichNode.maxHeight(rootNode)

    // now output the adjusted tree
    val output = new PrintWriter(config.outputTree.get.getAbsolutePath)
    output.write("[{\n")
    output.write(RichNode.toJSONOutput(rootNode, None,1.0))
    output.write("}]\n")
    output.close()

    val output2 = new PrintWriter(config.outputTree.get.getAbsolutePath + ".newick")
    output2.write(RichNode.toNewickString(rootNode) + ";\n")
    output2.close()


  }} getOrElse {
    println("Unable to parse the command line arguments you passed in, please check that your parameters are correct")
  }


}
