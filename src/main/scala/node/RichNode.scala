package main.scala.node

import beast.evolution.tree.Node
import beast.util.TreeParser
import main.scala.annotation.AnnotationsManager
import main.scala.cells.CellAnnotations
import main.scala.mix.EventContainer

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * This class takes a root BEAST node and creates a descriptive depth-first tree.  This richer node-type can
  * store all of the annotation data we're interested in, apply recursive functions, and
  * can generate JSON output
  */
case class RichNode(originalNd: Node,
                    annotations: AnnotationsManager,
                    parent: Option[RichNode],
                    numberOfTargets: Int = 10,
                    defaultNodeColor: String = "black") extends Ordered[RichNode] {

  var dontDeleteNode = false

  // store the orginal node for later if needed
  val originalNode = originalNd

  var name = originalNd.getID

  // are we a grafted node
  var graftedNode = false

  // handle some basic annotations about the node
  val myAnnotations = annotations.annotationMapping.get(name)

  var distToParent = originalNd.getHeight - (if (parent.isDefined) parent.get.originalNd.getHeight else 0)
  var height = originalNd.getHeight

  var color = "black"
  var nodeColor = defaultNodeColor

  // is this node part of a caterpillar node
  var inACatapiller = false
  var caterpillarDepth = 0.0

  var sampleName = "UNKNOWN"
  var count = 0
  val freeAnnotations = new mutable.HashMap[String, String]()

  // setup a store for our organ/taxa proportions, which we'll update with counts from the
  // children nodes
  var sampleProportions = new mutable.HashMap[String, Double]()

  // do we have taxa counts to fill in? internal nodes won't have these values but leaves will
  myAnnotations.map { annotation => {
    sampleName = annotations.cladeMapping(annotation.sample).clade // myAnnotations.get.sample
    color = annotations.cladeMapping(annotation.sample).color
    count = annotation.count
    sampleProportions(sampleName) = annotation.proportion
    annotation.additionalEntries.map { case (k, v) => {
      freeAnnotations(k) = v
    }
    }
  }
  }

  // explicitly pull out our event string
  var eventString: Option[Array[String]] = if (myAnnotations.isDefined) Some(myAnnotations.get.event) else None

  // our parsimony events
  var parsimonyEvents = Array.fill(numberOfTargets)("NONE")

  // now store each of our children and store the events on each child branch we see
  var children = Array[RichNode]()
  var childrenEvents = Array[String]()

  originalNd.getChildren.asScala.foreach { nd => {
    children :+= RichNode(nd, annotations, Some(this), numberOfTargets)
  }
  }

  // ******************************************************************************************************
  // our member methods
  // ******************************************************************************************************

  /**
    * add a new child node; useful when we make trees piecewise
    *
    * @param nd the child node to graft on
    */
  def graftOnChild(nd: RichNode): Unit = {
    children :+= nd
    resetChildrenAnnotations()
  }

  /**
    * add a new child node to a specific sub-node. Do a tree-traversal to find the correct node
    * and attach the node as a child
    *
    * @param nd the child node to graft on
    */
  def graftToName(name: String, nd: RichNode): Boolean = {
    val replacedHere = if (this.name == name) {
      children :+= nd
      true
    } else false

    children.map { cd =>
      cd.graftToName(name, nd)
    }.foldLeft(replacedHere)((a, b) => a | b)

  }

  /**
    * Sort the children of this node using a hierarchy of annotations
    *
    * @param sortingAnnotations an array of annotations to sort on, in order (alphabetically)
    */
  def sortChildren(sortingAnnotations: Array[String]) {

    children = RichNode.sortNodes(children, sortingAnnotations)

    children.foreach(cd => cd.sortChildren(sortingAnnotations))
  }

  /**
    * find the specified node by name in our tree
    *
    * @param name the node to find
    * @return A RichNode if found, None if not
    */
  def findSubnode(name: String): Option[RichNode] = {
    val replacedHere = if (this.name == name) Some(this) else None

    val childs = children.map { cd =>
      cd.findSubnode(name)
    }.flatten

    if ((replacedHere.isDefined && childs.size > 0) ||
      (!replacedHere.isDefined && childs.size > 1))
      throw new IllegalStateException("Found too many nodes with the name: " + name)

    if (replacedHere.isDefined || childs.size == 0)
      replacedHere
    else
      Some(childs(0))
  }


  /**
    * When the tree structure changes in any way, redo the auto-generated annotations. This saves us the trouble of trying
    * to figure out which ones are still ok, which ones are broken, etc.
    */
  def resetChildrenAnnotations(): Unit = {
    childrenEvents = Array[String]()
    sampleProportions = new mutable.HashMap[String, Double]()
    if (myAnnotations.isDefined) {
      sampleProportions(annotations.cladeMapping(myAnnotations.get.sample).clade) = myAnnotations.get.proportion
    }

    children.foreach { newChild => {
      // get the aggregate children events
      newChild.eventString.foreach(mp => childrenEvents :+= mp.mkString(annotations.eventSeperator))
      newChild.childrenEvents.foreach { mp => childrenEvents :+= mp }

      // add to our existing taxa proportions
      newChild.sampleProportions.foreach {
        case (sample, proportion) => sampleProportions(sample) = sampleProportions.getOrElse(sample, 0.0) + proportion
      }

    }
    }
  }

  /**
    * find the shared edits in children nodes
    *
    * @return the string of edits in common in our children, if nothing is shared we use '*'
    */
  def sharedEdits(): Array[String] = {
    val storage = Array.fill[mutable.HashSet[String]](numberOfTargets)(mutable.HashSet[String]())
    childrenEvents.foreach { event =>
      event.split(annotations.eventSeperator).zipWithIndex.foreach { case (edit, index) => storage(index) += edit }
    }
    storage.map { st => if (st.size == 1) st.iterator.next else "*" }
  }

  // figure out the shared events
  def parsimonyGenotypeDistance(otherNode: RichNode): Int = {
    parsimonyEvents.zip(otherNode.parsimonyEvents).map { case (evt1, evt2) => if (evt1 == evt2) 0 else 1 }.sum
  }

  // are we wild-type or not?
  def getConsistency(): String = {
    if (this.parsimonyEvents.map { mp => if (mp == "NONE") 0 else 1 }.sum == 0)
      return "WT"
    return "NOTWT"
  }

  // some recursive counting functions
  def countSubNodes(): Int = 1 + children.size + children.map { chd => chd.countSubNodes() }.sum

  def countSubUMIs(): Int = {
    val myCount = if (myAnnotations.isDefined) myAnnotations.get.count else 0
    myCount + children.map { chd => chd.countSubUMIs() }.sum
  }

  def countSubProportions(): Double = {
    val myCount = if (myAnnotations.isDefined) myAnnotations.get.proportion else 0.0
    myCount + children.map { chd => chd.countSubUMIs() }.sum
  }

  /**
    * we order RichNodes based on their parsimony allele string
    *
    * @param that the other node to compare to
    * */
  def compare(that: RichNode) = {
    val ret = this.parsimonyEvents.zip(that.parsimonyEvents).map { case (thisEvent, thatEvent) => {
      if (thisEvent == thatEvent)
        0
      else if (thisEvent == "NONE")
        -1
      else if (thatEvent == "NONE")
        1
      else if (thisEvent.compareTo(thatEvent) > 0)
        1
      else
        -1
    }
    }.sum
    //println(this.parsimonyEvents.mkString("_") + " " + that.parsimonyEvents.mkString("_") + ret)
    ret
  }
}

/**
  * We use the static methods here to transform nodes after they're created.
  */
object RichNode {
  def toRichTree(inputTree: TreeParser, annotationsManager: AnnotationsManager, numberOfTargets: Int): RichNode = {
    return RichNode(inputTree.getRoot, annotationsManager, None, numberOfTargets)
  }

  /**
    * we want to carry the grafted color downwards into the tree
    *
    * @param node the RichNode to recurse on
    */
  def fixGraftedColors(node: RichNode, graftedColor: String, passedGrafted: Boolean = false): Unit = {
    if (passedGrafted | node.graftedNode)
      node.nodeColor = graftedColor
    node.children.foreach { child =>
      fixGraftedColors(child, graftedColor, passedGrafted | node.graftedNode)
    }
  }


  /**
    * we want to propagate the DNA/RNA counts
    *
    * @param node the RichNode to recurse on
    */
  def fixDNASource(node: RichNode): Tuple2[Int, Int] = {
    var rna_total = node.freeAnnotations.getOrElse("rna_count", "0").toInt
    var dna_total = node.freeAnnotations.getOrElse("dna_count", "0").toInt
    node.children.foreach { child =>
      val sub_totals = fixDNASource(child)
      rna_total += sub_totals._1
      dna_total += sub_totals._2
    }
    node.freeAnnotations("DNA_total") = dna_total.toString
    node.freeAnnotations("RNA_total") = rna_total.toString
    node.freeAnnotations("RNA_prop") = (rna_total.toDouble / (rna_total.toDouble + dna_total.toDouble)).toString
    (rna_total, dna_total)
  }


  /**
    * we want to carry the grafted color downwards into the tree
    *
    * @param node the RichNode to recurse on
    */
  def toNewickString(node: RichNode): String = {
    if (node.children.size == 0)
      return node.name + ":1.0"
    else
      "(" + node.children.map { child =>
        RichNode.toNewickString(child)
      }.mkString(",") +
        "):1.0"
  }

  def toDouble(s: String): Option[Double] = {
    try {
      Some(s.toDouble)
    } catch {
      case e: Exception => None
    }
  }

  /**
    * Sort nodes based on alphabetical ordering of a series of annotations
    *
    * @param orderedAnnotations the annotations
    */
  def sortNodes(nodes: Array[RichNode], orderedAnnotations: Array[String]): Array[RichNode] = {

    if (orderedAnnotations.size == 0)
      return nodes

    val firstAnnotation = orderedAnnotations(0)

    val unAnnotatedBuilder = new ArrayBuffer[RichNode]()

    if (testDoubleConversation(nodes, firstAnnotation)) {
      //println("SORTING DOUBLE " + firstAnnotation)
      sortNodesDoubleValue(nodes, orderedAnnotations, unAnnotatedBuilder)
    } else {
      //println("SORTING String " + firstAnnotation)
      sortNodesStringValue(nodes, orderedAnnotations, unAnnotatedBuilder)
    }
  }

  /**
    * test if this annotation can be converted to a number
    *
    * @param nodes      our list of nodes
    * @param annotation the annotation to test for conversion
    * @return true if all values can convert to a number (or is empty)
    */
  def testDoubleConversation(nodes: Array[RichNode], annotation: String): Boolean = {
    var isNumber = true
    nodes.foreach { nd => if ((nd.freeAnnotations contains annotation)) isNumber = isNumber & toDouble(nd.freeAnnotations(annotation)).isDefined }
    isNumber
  }

  /**
    * sort nodes based on a numberic value
    *
    * @param nodes              the nodes we want to sort
    * @param orderedAnnotations the list of annotations
    * @param unAnnotatedBuilder
    * @return
    */
  private def sortNodesDoubleValue(nodes: Array[RichNode],
                                   orderedAnnotations: Array[String],
                                   unAnnotatedBuilder: ArrayBuffer[RichNode]): Array[RichNode] = {

    val lastAnnotationConstant = "1000000000"
    val firstAnnotation = orderedAnnotations(0)

    // first discover the values of the annotations, and then store them in order
    val sortedAnnotation = mutable.SortedMap[Double, ArrayBuffer[RichNode]]()

    nodes.foreach { case (node) => {
      val annotationValue = toDouble(node.freeAnnotations.getOrElse(firstAnnotation, lastAnnotationConstant)).get
      //println(annotationValue + " from " + node.freeAnnotations.getOrElse(firstAnnotation, lastAnnotationConstant))
      if (annotationValue == lastAnnotationConstant) {
        unAnnotatedBuilder += node
      } else {
        val builder = sortedAnnotation.getOrElse(annotationValue, new ArrayBuffer[RichNode]())

        builder += node

        sortedAnnotation(annotationValue) = builder
      }
    }
    }

    // then sort sub populations, in order
    val remainingAnnotations = orderedAnnotations.slice(1, orderedAnnotations.size)
    val resorted = sortedAnnotation.map { case (value, nodes) => {
      (value, sortNodes(nodes.toArray, remainingAnnotations))
    }
    }

    // and do the same for the nodes without an annotation
    val resortedUnknown = sortNodes(unAnnotatedBuilder.toArray, remainingAnnotations)

    // then flatten and return the sorted array
    val finalSorted = new ArrayBuffer[RichNode]
    resorted.foreach { case (annot, nodes) => nodes.foreach(node => finalSorted += node) }
    resortedUnknown.foreach(node => finalSorted += node)
    finalSorted.toArray
  }


  /**
    * sort nodes based on a string value
    *
    * @param nodes
    * @param orderedAnnotations
    * @param unAnnotatedBuilder
    * @return
    */
  private def sortNodesStringValue(nodes: Array[RichNode],
                                   orderedAnnotations: Array[String],
                                   unAnnotatedBuilder: ArrayBuffer[RichNode]): Array[RichNode] = {

    val lastAnnotationConstant: String = "__LAST__"
    val firstAnnotation = orderedAnnotations(0)

    // first discover the values of the annotations, and then store them in order
    val sortedAnnotation = mutable.SortedMap[String, ArrayBuffer[RichNode]]()

    nodes.foreach { case (node) => {
      val annotationValue = node.freeAnnotations.getOrElse(firstAnnotation, lastAnnotationConstant)

      if (annotationValue == lastAnnotationConstant) {
        unAnnotatedBuilder += node
      } else {
        val builder = sortedAnnotation.getOrElse(annotationValue, new ArrayBuffer[RichNode]())

        builder += node

        sortedAnnotation(annotationValue) = builder
      }
    }
    }

    // then sort sub populations, in order
    val remainingAnnotations = orderedAnnotations.slice(1, orderedAnnotations.size)
    val resorted = sortedAnnotation.map { case (value, nodes) => {
      (value, sortNodes(nodes.toArray, remainingAnnotations))
    }
    }

    // and do the same for the nodes without an annotation
    val resortedUnknown = sortNodes(unAnnotatedBuilder.toArray, remainingAnnotations)

    // then flatten and return the sorted array
    val finalSorted = new ArrayBuffer[RichNode]
    resorted.foreach { case (annot, nodes) => nodes.foreach(node => finalSorted += node) }
    resortedUnknown.foreach(node => finalSorted += node)
    finalSorted.toArray
  }

  /**
    *
    * @param node the RichNode to recurse on
    * @param ourBranchJustOrgan
    */
  def assignBranchColors(node: RichNode, ourBranchJustOrgan: String = "false"): Unit = {
    node.freeAnnotations("justOrganSplit") = ourBranchJustOrgan

    // if we have children, check if their node all share the same edit, if so
    // propigate the 'gray' color to them
    if (node.children.size > 0) {
      val counts = node.children.map { child => child.parsimonyEvents.mkString("_") }
      node.children.map { child => {
        val newColor = counts.count(_ == child.parsimonyEvents.mkString("_")) match {
          case x if x > 1 => "true"
          case _ => "false"
        }
        assignBranchColors(child, newColor)
      }
      }
    }
  }

  /**
    * add children cells to the terminal leaf nodes
    *
    * @param node       the RichNode to recurse on
    * @param childAnnot the cell annotation object
    */
  def addCells(node: RichNode, childAnnot: CellAnnotations, addedColor: String = "white"): Unit = {

    if (node.children.size > 0) {
      node.children.foreach { child => {
        addCells(child, childAnnot, addedColor)
      }
      }
    } else {
      val cellsToAdd = childAnnot.findMatchingCells(node.eventString.get.mkString("_"))
      cellsToAdd.foreach { cell => {
        val newONode = new Node(cell.name)
        newONode.setHeight(node.height + 1.0)
        val richN = RichNode(newONode, node.annotations, Some(node), node.numberOfTargets, addedColor)
        richN.parsimonyEvents = cell.eventString.split("-")
        cell.additionalAnnotations.foreach { case (name, value) => {
          richN.freeAnnotations(name) = value
        }
        }
        //println("adding " + cellsToAdd.size + " cells")
        node.children :+= richN
      }
      }
    }
  }

  /**
    * given the results of the parsimony run, figure out the genotypes of each node in turn.
    * this means accumulating events from the root outwards, as single nodes only have the changes
    * compared to the previous node
    *
    * @param rootNode        the root node, which we assume is all NONE in camin-sokal parsimony
    * @param linker          provides a lookup for links between nodes
    * @param container       the event container
    * @param numberOfTargets the number of targets
    */
  def applyParsimonyGenotypes(rootNode: RichNode, linker: NodeLinker, container: EventContainer, numberOfTargets: Int = 10): Unit = {
    //println(numberOfTargets)
    // lookup each link between a subnode and the root, and assign it's genotypes recursively
    rootNode.children.foreach { newChild => recAssignGentoypes(rootNode, newChild, linker, container) }
  }

  /**
    * recursively walk down the tree assigning genotypes to each of the progeny nodes as we go.  We have to do
    * this as the parsimony output is recursive; they define each nodes identity in terms of it's parents
    * genotype with edit's overlaid
    *
    * @param parent the parent of this node
    * @param child  this node, the child
    * @param linker mapping parent to children nodes
    */
  def recAssignGentoypes(parent: RichNode, child: RichNode, linker: NodeLinker, eventContainer: EventContainer): Unit = {
    // copy the parents genotype over to the child
    parent.parsimonyEvents.zipWithIndex.foreach { case (edit, index) => child.parsimonyEvents(index) = edit }

    // find the link from out parent node -- there should only be one edge leading to this node ever
    val link = linker.lookupTos(child.name)
    assert(link.size > 0)


    // make a list of the events that we're adding
    link(0).chars.zipWithIndex.foreach { case (change, index) => change match {
      case ('.') => {
        /* do nothing */
      }
      case ('1') => {
        val event = eventContainer.columnToEvent(index) // our first position is an edit, not NONE
        event.positions.foreach { site => {
          // check to make sure we're not conflicting and overwriting an already edited site

          // TODO: fix this part
          if (child.parsimonyEvents.size > site) {
            if (child.parsimonyEvents(site) != "NONE") {
              println("WARNING: Conflict at site " + site + " for parent " + parent.name + " for child " + child.name + " event " + event)
            } else {
              child.parsimonyEvents(site) = event.eventString
            }
          }
        }
        }
      }
    }
    }

    // now for each of the children of this node, recursively assign genotypes
    child.children.foreach { newChild => recAssignGentoypes(child, newChild, linker, eventContainer) }
  }

  /**
    * recursively walk down the tree assigning genotypes to each of the progeny nodes as we go.  We have to do
    * this as the parsimony output is recursive; they define each nodes identity in terms of it's parents
    * genotype with edit's overlaid
    *
    * @param node node
    */
  def backAssignGenotypes(node: RichNode): Array[String] = {

    node.children.size match {
      case 0 => {
        node.parsimonyEvents = node.eventString.get
        //println("NODE GENOTYPE " + node.name + " node.parsimony " + node.parsimonyEvents.mkString("-"))
        node.eventString.get
      }
      case 1 => {
        node.parsimonyEvents = backAssignGenotypes(node.children(0))
        //println("NODE GENOTYPE " + node.name + " node.parsimony " + node.parsimonyEvents.mkString("-"))
        node.parsimonyEvents
      }
      case x if x > 1 => {
        node.parsimonyEvents = RichNode.commonEvents(node.children.map { child => backAssignGenotypes(child) })
        //println("NODE GENOTYPE " + node.name + " node.parsimony " + node.parsimonyEvents.mkString("-"))
        node.parsimonyEvents
      }
      case _ => {
        throw new IllegalStateException("Unable to process nodes with too many nodes: size = " + node.children.size)
      }
    }
  }

  /**
    * find the common events in a set of arrays
    *
    * @param events an array of arrays, which contain events over target regions
    * @return the common events
    */
  def commonEvents(events: Array[Array[String]]): Array[String] = {
    assert(events.size > 0, "Events.size was less than one, please pass in at least one event")
    var ret = events(0).map { tr => tr }
    events.slice(1, events.size).foreach { evt =>
      assert(evt.size == ret.size, "We saw an event set with a different number of events than the previous set")
      evt.zipWithIndex.foreach { case (e, index) => if (!(ret(index) == e)) ret(index) = "NONE" }
    }
    ret
  }

  /**
    * our internal names come off the tree without names, even though the genotypes are assigned a name.  What we have
    * to do here is traverse to the leaves, which do have names, and walk backwards assigning parent names from known
    * leaf child->parent output
    *
    * @param node   the node to assign a name to
    * @param linker the parent and child relationships
    * @return the string of the top node (lazy, this is used to recursively fill in names on the way
    *         back up)
    */
  def recAssignNames(node: RichNode, linker: NodeLinker): String = {
    // if we have a leaf -- where there are no children -- assign the name
    if (node.children.size == 0) {
      if (node.name == null || node.name == "null") {
        val edge = linker.lookupTos(node.name)
        assert(edge.size > 0)
        return edge(0).from
      }
    } else {
      val names = node.children.map { case (nd) => recAssignNames(nd, linker) }.toSet.toList
      if (node.name == null || node.name == "null") {
        names.foreach{name => {
          try {
            val edge = linker.lookupTos(names(0))
            assert(edge.size > 0)
            return edge(0).from
          } catch {
            case e: Exception => {}
          }
        }}
      }
    }
      return node.name

  }

  def recAssignNames_old(node: RichNode, linker: NodeLinker): String = {
    // if we have a leaf -- where there are no children -- assign the name
    if (node.children.size == 0) {
      val edge = linker.lookupTos(node.name)
      assert(edge.size > 0)
      return edge(0).from
    } else {
      val names = node.children.map { case (nd) => recAssignNames(nd, linker) }.toSet.toList
      if (names.size == 1) {
        node.name = names(0)
        val edge = linker.lookupTos(names(0))
        assert(edge.size > 0)
        return edge(0).from
      }
      else {
        return node.name
      }
    }
  }

  /**
    * sort the nodes by their allele string
    *
    * @param node the node to start sorting on
    */
  def reorderChildrenByAlleleString(node: RichNode): Unit = {

    if (node.children.size > 0)
      print(node.children(0).count + " -> ")
    scala.util.Sorting.quickSort(node.children)
    //if (node.children.size > 0)
    //  println(node.children(0).count)
    node.children.foreach { case (nd) => reorderChildrenByAlleleString(nd) }
  }

  /**
    * apply a function to all nodes in depth first order, storing the results in the annotations
    *
    * @param node the node to start sorting on
    */
  def applyFunction(node: RichNode, func: (RichNode, Option[RichNode]) => Tuple2[String, String]): Unit = {
    val res = func(node, node.parent)
    node.freeAnnotations(res._1) = res._2
    node.children.foreach { case (nd) => applyFunction(nd, func) }
  }

  /**
    * check that our nodes are assigned consistent node identities between the parsimony and known annotations
    *
    * @param node the node
    */
  def recCheckNodeConsistency(node: RichNode): Unit = {
    // if we have a leaf -- where there are no children -- assign the name
    if (node.children.size == 0 && node.eventString.isDefined) {
      val differences = node.eventString.get.zip(node.parsimonyEvents).map { case (evt1, evt2) => if (evt1 == evt2) 0 else 1 }.sum
      if (differences > 0) {
        println("FAIL " + node.eventString.get.mkString(",") + " - " + node.parsimonyEvents.mkString(","))
        println("Defaulting to known event state... please check this in the tree")
        node.eventString.get.zipWithIndex.foreach { case (event, index) => node.parsimonyEvents(index) = event }

      }
    } else {
      node.children.map { case (nd) => recCheckNodeConsistency(nd) }.toSet.toList
    }
  }


  /**
    * aggregate a keyword set on leaves up to parents
    *
    * @param node         the initial node
    * @param keyword      the keyword to lookup in the node's free annotations
    * @param sep          the string used to join annotations
    * @param noAnnotation the string to use if a leaf doesn't have the annotation
    * @return
    */
  def aggregateKeyword(node: RichNode, keyword: String, sep: String, noAnnotation: String = "NONE"): String = {
    // if we have a leaf -- where there are no children -- assign the name
    if (node.children.size == 0) {
      if (node.freeAnnotations contains keyword)
        node.freeAnnotations(keyword)
      else
        "NONE"
    } else {

      val aggreagatedKeyword = node.children.map { case (nd) => aggregateKeyword(nd, keyword, sep) }.toSet.toList.mkString(sep)
      node.freeAnnotations(keyword) = aggreagatedKeyword
      aggreagatedKeyword
    }
  }

  /**
    * find the depth of the deepest node on the tree
    *
    * @param node the node
    */
  def maxHeight(node: RichNode): Double = {
    if (node.children.size == 0)
      node.height
    else
      Math.max(node.height, node.children.foldLeft(0.0)((a, b) => Math.max(a, maxHeight(b))))
  }

  /**
    * this function recursively adds an annotation to a node indicating the depth of a caterpillar
    * node starting from any branch marked with the 'is_spine' annotation. We'll use this annotation to
    * add caterpillar nodes to the d3 trees
    *
    * @param node            the node we're looking at recursively
    * @param recursivePillar is our parent a pillar? determines how we perform the traversal
    */
  def annotateTopOfCaterpillar(node: RichNode, recursivePillar: Boolean): Double = {
    // if we're NOT in a caterpillar, but we start one, recursively find the deepest this 'pillar goes
    node.caterpillarDepth = (recursivePillar, node.inACatapiller) match {
      case (false, true) => {
        // we start a caterpillar; find the maximum caterpillar-only depth of our children and store that as our caterpillarDepth
        node.children.map { child => annotateTopOfCaterpillar(child, true) }.max
      }
      case (true, true) => {
        // we're in a caterpillar, and we're not yet the end, add our depth-to-parent to the longest of our children
        node.children.map { child => annotateTopOfCaterpillar(child, true) }.max + node.distToParent
      }
      case (true, false) => {
        // we're the first non-caterpillar node in a caterpillar chain. return 0
        0
      }
      case (false, false) => {
        // we're not in a caterpillar, and we're not marked as one, this is a zero
        0
      }
    }

    // now call this on all the children
    node.children.foreach { child => annotateTopOfCaterpillar(node, node.inACatapiller) }

    // and return our depth
    node.caterpillarDepth
  }

  /**
    * recursively output the current tree as a JSON string
    *
    * @param node         the node to start at
    * @param parent       it's parent node, optional (for recussion)
    * @param distToRoot   the distance to the root node, as we convert to a zero or one system
    * @param indentDepth  how many tabs we need
    * @param noParentName if we don't have a parent, what should we call the previous node
    * @return a JSON string representation
    */
  def toJSONOutput(node: RichNode, parent: Option[RichNode], distToRoot: Double, indentDepth: Int, noParentName: String = "null"): String = {
    val outputString = new ArrayBuffer[String]()

    val indent = "\t" * indentDepth

    outputString += indent + RichNode.toJSON("name", node.name)
    outputString += indent + RichNode.toJSON("parent", if (parent.isDefined) parent.get.name else noParentName)
    outputString += indent + RichNode.toJSON("length", 1)
    outputString += indent + RichNode.toJSON("rootDist", distToRoot)
    outputString += indent + RichNode.toJSON("subNodes", node.countSubNodes())
    outputString += indent + RichNode.toJSON("totatSubNodes", node.countSubProportions())
    outputString += indent + RichNode.toJSON("color", node.color)
    outputString += indent + RichNode.toJSON("nodecolor", node.nodeColor)
    outputString += indent + RichNode.toJSON("grafted", node.graftedNode.toString)

    outputString += indent + RichNode.toJSON("sample", node.sampleName)
    node.freeAnnotations.foreach { case (key, value) =>
      outputString += indent + RichNode.toJSON(key, value)
    }
    // TODO: fix this proportions stuff
    val sampleTot = if (node.annotations.sampleTotals contains node.sampleName) node.annotations.sampleTotals(node.sampleName) else 0
    outputString += indent + RichNode.toJSON("organCountsMax", sampleTot)
    outputString += indent + RichNode.toJSON("cladeTotal", node.count)
    outputString += indent + RichNode.toJSON("max_organ_prop", if (sampleTot.toDouble > 0) node.count.toDouble / sampleTot.toDouble else 0.0)
    outputString += indent + RichNode.toJSON("event", node.parsimonyEvents.mkString(node.annotations.eventSeperator))
    outputString += indent + RichNode.toJSON("commonEvent", node.sharedEdits.mkString(node.annotations.eventSeperator))
    outputString += indent + "\"organProportions\": { \n"
    outputString += node.sampleProportions.map { case (sample, prop) => indent + RichNode.toJSON(sample, prop, terminator = "") }.mkString(",\n")
    outputString += "\n},\n"
    if (node.children.size > 0) {
      outputString += indent + "\"children\": [{\n"
      outputString += node.children.map { child => RichNode.toJSONOutput(child, Some(node), distToRoot + 1.0, indentDepth + 1) }.mkString("},\n{\n")
      outputString += indent + "}],\n"
    }
    outputString += indent + RichNode.toJSON("consistency", node.getConsistency, terminator = "\n")
    outputString.toArray.mkString("")
  }

  /**
    * generate a flat table version of our results
    *
    * @param node         the node to start at
    * @param parent       it's parent node, optional (for recussion)
    * @param distToRoot   the distance to the root node, as we convert to a zero or one system
    * @param indentDepth  how many tabs we need
    * @param noParentName if we don't have a parent, what should we call the previous node
    * @return a JSON string representation
    */
  def toFlatTableOutput(node: RichNode, parent: Option[RichNode], distToRoot: Double, indentDepth: Int, noParentName: String = "null"): String = {
    val outputString = new ArrayBuffer[String]()

    outputString += RichNode.toKeyValue(node.name, "name", "name", node.name)
    outputString += RichNode.toKeyValue(node.name, "parent", "parent", if (parent.isDefined) parent.get.name else noParentName)
    outputString += RichNode.toKeyValue(node.name, "length", "length", 1)
    outputString += RichNode.toKeyValue(node.name, "rootDist", "rootDist", distToRoot)
    outputString += RichNode.toKeyValue(node.name, "subNodes", "subNodes", node.countSubNodes())
    outputString += RichNode.toKeyValue(node.name, "totatSubNodes", "totatSubNodes", node.countSubProportions())
    outputString += RichNode.toKeyValue(node.name, "color", "color", node.color)
    outputString += RichNode.toKeyValue(node.name, "nodecolor", "nodecolor", node.nodeColor)
    outputString += RichNode.toKeyValue(node.name, "grafted", "grafted", node.graftedNode.toString)

    outputString += RichNode.toKeyValue(node.name, "sample", "sample", node.sampleName)
    node.freeAnnotations.foreach { case (key, value) =>
      outputString += RichNode.toKeyValue(node.name, key, key, value)
    }

    val sampleTot = if (node.annotations.sampleTotals contains node.sampleName) node.annotations.sampleTotals(node.sampleName) else 0
    outputString += RichNode.toKeyValue(node.name, "organCountsMax", "organCountsMax", sampleTot)
    outputString += RichNode.toKeyValue(node.name, "cladeTotal", "cladeTotal", node.count)
    outputString += RichNode.toKeyValue(node.name, "max_organ_prop", "max_organ_prop", if (sampleTot.toDouble > 0) node.count.toDouble / sampleTot.toDouble else 0.0)
    outputString += RichNode.toKeyValue(node.name, "event", "event", node.parsimonyEvents.mkString(node.annotations.eventSeperator))
    outputString += RichNode.toKeyValue(node.name, "commonEvent", "commonEvent", node.sharedEdits.mkString(node.annotations.eventSeperator))
    outputString += node.sampleProportions.map { case (sample, prop) => RichNode.toKeyValue(node.name, "organProportions", sample, prop) }.mkString("")
    outputString += RichNode.toKeyValue(node.name, "consistency", "consistency", node.getConsistency)
    if (node.children.size > 0) {
      outputString += node.children.map { child => RichNode.toFlatTableOutput(child, Some(node), distToRoot + 1.0, indentDepth + 1) }.mkString("")
    }
    outputString.toArray.mkString("")
  }

  /**
    * output 4-part key-value pairs
    *
    * @param sample
    * @param name
    * @param index
    * @param value
    * @param terminator
    * @return
    */
  def toKeyValue(sample: String, name: String, index: String, value: Any, sep: String = "\t", terminator: String = "\n"): String = {
    val sm = if (sample == null) "null" else sample
    val nm = if (name == null) "null" else name
    val id = if (index == null) "null" else index
    val vl = if (value == null) "null" else value
    return (nm + sep + nm + sep + id + sep + vl.toString + terminator)
  }

  def toJSON(name: String, simple: Any, terminator: String = ",\n"): String = simple match {
    case x: String => "\"" + name + "\" : \"" + x + "\"" + terminator
    case x: Int => "\"" + name + "\" : " + x + terminator
    case x: Double => "\"" + name + "\" : " + x + terminator
    case x: Float => "\"" + name + "\" : " + x + terminator
    case x if x == null => "\"" + name + "\" : \"null\"" + terminator
    case _ => throw new IllegalStateException("We don't know what to do for " + simple)
  }
}