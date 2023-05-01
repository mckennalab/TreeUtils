package main.scala

import java.io.{File, PrintWriter}

import com.typesafe.scalalogging.LazyLogging
import main.scala.annotation.AnnotationsManager
import main.scala.cells.CellAnnotations
import main.scala.mix.MixRunner.CacheApproach
import main.scala.mix._
import main.scala.node.RichNode
import picocli.CommandLine

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

class MixMain extends Runnable with LazyLogging {


  // ------------------------------------------------------------------------------------------------------------
  // input files
  // ------------------------------------------------------------------------------------------------------------
  @CommandLine.Option(names = Array("-allelesFile", "--allelesFile"), required = true, paramLabel = "FILE",
    description = Array("the binary off-target database to read from"))
  private var allelesFile: String = ""

  @CommandLine.Option(names = Array("-annotations", "--annotations"), required = false, paramLabel = "FILE",
    description = Array("the annotation file for individual cells"))
  private var allCellAnnotations: String = ""

  @CommandLine.Option(names = Array("-useCached", "--useCached"), required = false, paramLabel = "FILE",
    description = Array("should we used a cached (previous) MIX run in the specified directory"))
  private var useCached: Boolean = false

  // ------------------------------------------------------------------------------------------------------------
  // output files
  // ------------------------------------------------------------------------------------------------------------
  @CommandLine.Option(names = Array("-outputTree", "--outputTree"), required = true, paramLabel = "FILE",
    description = Array("the output tree we'll produce"))
  private var outputTree: String = ""

  @CommandLine.Option(names = Array("-outputTable", "--outputTable"), required = true, paramLabel = "FILE",
    description = Array("a flat file output table"))
  private var outputTableFile: String = ""


  @CommandLine.Option(names = Array("-sample", "--sample"), required = true, paramLabel = "FILE",
    description = Array("the sample name"))
  private var sample: String = "UNKNOWN"

  // ------------------------------------------------------------------------------------------------------------
  // How to run the MIX executable
  // ------------------------------------------------------------------------------------------------------------
  @CommandLine.Option(names = Array("-mixRunLocation", "--mixRunLocation"), required = true, paramLabel = "FILE",
    description = Array("The directory in which to execute MIX, which will serve as a cache directory for this run"))
  private var mixRunLocation: String = ""

  @CommandLine.Option(names = Array("-mixEXEC", "--mixEXEC"), required = true, paramLabel = "FILE",
    description = Array("the path for the MIX executable"))
  private var mixExecutableLocation: String = ""

  @CommandLine.Option(names = Array("-subsetFirstX", "--subsetFirstX"), required = false, paramLabel = "FILE",
    description = Array("Use the first X targets to build the tree, then use the remaining targets to build sub-trees"))
  private var firstX = -1

  @CommandLine.Option(names = Array("-clusterLabel", "--clusterLabel"), required = false, paramLabel = "FILE",
    description = Array("use a column in the text file to define clusters as the first level of the tree"))
  private var clusterLabel : Option[String] = None


  @CommandLine.Option(names = Array("-sortByAnnotations", "--sortByAnnotations"), required = false, paramLabel = "FILE",
    description = Array("a list of annotations to sort cells by"))
  private var sortByAnnotations: String = ""

  def run() {
    // set the MIX run location
    MixRunner.mixLocation = new File(mixExecutableLocation)

    // ------------------------------------------------------------
    // read in the list of alleles, either as a 'allReadsCounts' summary file from the GESTALT pipeline, or as a text
    // file that contains each cell's allele (with duplicate alleles)
    // ------------------------------------------------------------
    val readEventsObj = if (new File(allelesFile).getAbsolutePath.endsWith("allReadCounts")) {
      EventIO.readEventsObject(new File(allelesFile), sample)
    } else if (new File(allelesFile).getAbsolutePath.endsWith("txt")) {
      EventIO.readCellObject(new File(allelesFile), sample)
    } else {
      throw new IllegalStateException("Unable to determine lineage barcode filetype for " + allelesFile)
    }

    // ------------------------------------------------------------
    // add any annotations we have
    // ------------------------------------------------------------
    val annotationMapping = new AnnotationsManager(readEventsObj)

    // ------------------------------------------------------------
    // process a single tree by calling MIX (pulling cached data if available and requested)
    // ------------------------------------------------------------

    val rootNode = if (firstX > 0) {
      println("Running split-tree...")
      EventSplitter.splitBySubsetTargets(new File(mixRunLocation),
        readEventsObj,
        firstX,
        sample,
        annotationMapping)
    } else {
      println("Running single tree...")
      val cacheApproach = if (useCached) CacheApproach.USE_CACHE else CacheApproach.NO_OVERWRITE

      val (rootNode, linker) = MixRunner.mixOutputToTree(
        MixRunner.runMix(new File(mixRunLocation), readEventsObj, cacheApproach), readEventsObj, annotationMapping, "root")
      MixRunner.postProcessTree(rootNode, linker, readEventsObj, annotationMapping)

      rootNode
    }

    // ------------------------------------------------------------
    // If asked, add cells to the leaf nodes
    // ------------------------------------------------------------
    if (allCellAnnotations != "") {
      val childAnnot = new CellAnnotations(new File(allCellAnnotations))
      RichNode.addCells(rootNode, childAnnot, "white")
      childAnnot.printUnmatchedCells()
      println("Resetting child annotations after adding cell annotations...")
      rootNode.resetChildrenAnnotations()
      RichNode.aggregateKeyword(rootNode, "name", ",")

      // fix the total sum of the DNA/RNA counts
      RichNode.fixDNASource(rootNode);

    }

    // sort the order of children node according to an annotation
    if (sortByAnnotations != "") {
      val splitAnnotations = sortByAnnotations.split(",")
      rootNode.sortChildren(splitAnnotations)
    }

    // ------------------------------------------------------------
    // traverse the nodes and add names to any internal nodes without names
    // ------------------------------------------------------------

    // get an updated height to flip the tree around
    val maxHeight = RichNode.maxHeight(rootNode)

    // now output the adjusted tree
    val output = new PrintWriter(new File(outputTree).getAbsolutePath)
    output.write("[{\n")
    output.write(RichNode.toJSONOutput(rootNode, None, 1.0, 0))
    output.write("}]\n")
    output.close()

    // now output the adjusted tree
    val outputTable = new PrintWriter(outputTableFile)
    outputTable.write("node\tannotation\tkey\tvalue\n")
    outputTable.write(RichNode.toFlatTableOutput(rootNode, None, 1.0, 0))
    outputTable.close()

    val output2 = new PrintWriter(new File(outputTree).getAbsolutePath + ".newick")
    output2.write(RichNode.toNewickString(rootNode) + ";\n")
    output2.close()
  }

}
object MixMain {
  def main(args: Array[String]) {
    CommandLine.run(new MixMain(), System.err, args: _*)
  }
}