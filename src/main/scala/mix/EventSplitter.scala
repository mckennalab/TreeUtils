package main.scala.mix

import java.io.File

import beast.evolution.tree.Node
import main.scala.annotation.AnnotationsManager
import main.scala.mix.MixRunner.CacheApproach
import main.scala.node.{NodeLinker, RichNode}
import main.scala.stats.Barcode

import scala.collection.mutable
import scala.collection.mutable.HashMap

/**
  * split events into a pre and post pile, generate trees seperately for each, and
  * put the whole tree back together
  *
  */
object EventSplitter {

  def splitInputByAnnotation(eventContainer: EventContainer,
                             annotationName: String): HashMap[String,EventContainer] = {


    //if (!(eventContainer.rawAnnotations contains annotationName)) {
    //  throw new IllegalStateException("Unable to find annotation column to split events on named: " + annotationName)
    //}

    val barcodeLookup = eventContainer.events.map{case(e) => (e.name,e)}.toMap

    val returnMapping = new HashMap[String,Array[Barcode]]()
    eventContainer.rawAnnotations.zipWithIndex.map{case((key,mapping),index) => {
      mapping.get(annotationName) match {
        case Some(str) =>
          if (returnMapping contains str) {
            var entry = returnMapping.get(str).get
            returnMapping(str) = entry ++ Array(barcodeLookup.get(key).get)
          } else {
            returnMapping(str) = Array(barcodeLookup.get(key).get)
          }
        case None => {}
      }
    }
    }

    returnMapping.map{case(k,v) => (k,SubsettedEventContainer(v,eventContainer))}
  }


  def splitByAnnotation(mixDir: File,
                        eventContainer: EventContainer,
                        annotationName: String,
                        sample: String,
                        annotationMapping: AnnotationsManager,
                        numberOfTargets: Int,
                        graftedNodeColor: String = "red"): RichNode = {


    // split the events into the root for the first X sites,
    // and sub-tree for individual nodes
    val bareRoot = new Node("annotation_root")
    val linker = new NodeLinker()
    val baseNode = RichNode(bareRoot, annotationMapping,None,numberOfTargets)
    baseNode.graftedNode = true
    println("number of columns " + EventInformation.numberOfColumns())
      val split_annotations = EventSplitter.splitInputByAnnotation(eventContainer, annotationName)

    split_annotations.foreach {
      case (annotation, childrenByAnnotation) => {

        // MIX can't make a meaningful tree out of less than three nodes
        if (childrenByAnnotation.events.size > 2) {

          println("Processing the " + annotation + " tree with kids ")
          val (childNode, childLinker) = MixRunner.mixOutputToTree(MixRunner.runMix(mixDir, childrenByAnnotation, CacheApproach.OVERWRITE),
            childrenByAnnotation, annotationMapping, annotation, graftedNodeColor)
          childNode.name = annotation

          childLinker.shiftEdges(linker.getMaximumInternalNode, annotation)
          linker.addEdge(Edge(baseNode.name,childNode.name,baseNode.name))
          linker.addEdges(childLinker)

          childNode.name = annotation
          childNode.graftedNode = true
          childNode.dontDeleteNode = true

          childNode.eventString = Some(Array.fill[String](numberOfTargets)("NONE"))
          println("GRRaaaaaaaaR222 " + childNode.eventString)
          baseNode.graftOnChild(childNode)
        } else {
          val baseAnnotation = new RichNode(new Node(annotation),
            annotationMapping, Some(baseNode), numberOfTargets)

          baseAnnotation.eventString = Some(Array.fill[String](numberOfTargets)("NONE"))
          baseAnnotation.graftedNode = true
          baseAnnotation.dontDeleteNode = true
          childrenByAnnotation.events.foreach { child => {

            linker.addEdge(Edge(baseAnnotation.name, child.name, baseNode.name))

            println("GRRR2AA " + EventInformation.numberOfColumns())
            println("GRRR222 " + child.events)

            val newNode = RichNode(new Node(child.name), annotationMapping, Some(baseAnnotation), EventInformation.numberOfColumns(), graftedNodeColor)
            newNode.eventString = Some(child.events)
            baseAnnotation.graftOnChild(newNode)
          }
          }
          baseNode.graftOnChild(baseAnnotation)
          linker.addEdge(Edge(baseNode.name, baseAnnotation.name, baseNode.name))
        }
      }
    }

    // post-process the final tree
    MixRunner.postProcessTree(baseNode, linker, eventContainer, annotationMapping)


  }


  // TODO: preserve the underlying name
  def splitBySubsetTargets(mixDir: File,
                           eventContainer: EventContainer,
                           firstXSites: Int,
                           sample: String,
                           annotationMapping: AnnotationsManager,
                           graftedNodeColor: String = "red",
                           useCache: Boolean = false): RichNode = {

    // setup an array of wildcards over the sites we want to split on
    val sites = (0 until firstXSites).map {
      id => (id, EventContainer.wildcard)
    }.toArray

    // split the events into the root for the first X sites,
    // and sub-tree for individual nodes
    val rootTreeContainer = EventContainer.subset(eventContainer, sites, sample)

    // run the root tree, and fetch the results
    println("Processing the root tree with nodes " + rootTreeContainer._1.events.map{evt => evt.events.mkString("_")}.mkString(", "))
    val (rootNodeAndConnection,linker) = MixRunner.mixOutputToTree(MixRunner.runMix(mixDir, rootTreeContainer._1, CacheApproach.NO_OVERWRITE), rootTreeContainer._1, annotationMapping, "root")

    createSubTree(rootTreeContainer,linker, eventContainer, mixDir, rootNodeAndConnection, annotationMapping)

    // post-process the final tree
    MixRunner.postProcessTree(rootNodeAndConnection, linker, eventContainer, annotationMapping)

    rootNodeAndConnection
  }

  private def createSubTree(rootTreeContainer: (EventContainer, mutable.HashMap[String, Array[String]]),
                            linker: NodeLinker,
                            eventContainer: EventContainer,
                            mixDir: File,
                            rootNodeAndConnection: RichNode,
                            annotationMapping: AnnotationsManager,
                            graftedNodeColor: String = "red",
                           ): mutable.HashMap[String, RichNode] = {
    // now for each subtree, make a tree using just those events to be grafted onto the root tree
    // and graft the children onto the appropriate node
    val childToTree = new mutable.HashMap[String, RichNode]()

    rootTreeContainer._2.foreach {
      case (internalNodeName, children) => {

        // MIX can't make a meaningful tree out of less than three nodes
        if (children.size > 2) {
          val subset = EventContainer.subsetByChildren(eventContainer, children, internalNodeName)

          println("Processing the " + internalNodeName + " tree... " + rootTreeContainer._2(internalNodeName).mkString("^") + " with kids " + subset.events.map { evt => evt.prettyString() }.mkString("),("))
          val (childNode, childLinker) = MixRunner.mixOutputToTree(MixRunner.runMix(mixDir, subset, CacheApproach.OVERWRITE), subset, annotationMapping, internalNodeName, graftedNodeColor)

          childToTree(internalNodeName) = childNode
          childToTree(internalNodeName).graftedNode = true
          childLinker.shiftEdges(linker.getMaximumInternalNode, internalNodeName)
          linker.addEdges(childLinker)

          rootNodeAndConnection.graftToName(internalNodeName, childToTree(internalNodeName))
        } else {
          println("Less than three nodes " + children.mkString(","))
          // pull out the highlighted children
          val childenEvents = eventContainer.events.filter { evt => children.foldLeft[Boolean](false)((a, b) => (evt.name == b) | a) }
          assert(childenEvents.size < 3 && childenEvents.size > 0)

          val parentEvent = rootNodeAndConnection.findSubnode(internalNodeName)
          assert(parentEvent.isDefined, "unable to find parent for " + internalNodeName)

          childenEvents.foreach { child => {
            linker.addEdge(Edge(parentEvent.get.name, child.name, parentEvent.get.name))

            val newNode = RichNode(new Node(child.name), annotationMapping, parentEvent, child.events.size, graftedNodeColor)
            newNode.graftedNode = true
            rootNodeAndConnection.graftToName(internalNodeName, newNode)
          }
          }
        }
      }
    }
    childToTree
  }
}