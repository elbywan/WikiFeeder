package parsing

import java.util.concurrent._
import java.util.{UUID, Date}

import _root_.parsing.Worker.LimitedQueue
import org.neold.adapters.Adapters.TransactionalAdapter
import scala.collection.immutable.Stack
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.xml._
import org.neold.core.Neold, org.neold.core.Neold._
import java.util.concurrent.TimeUnit


object Worker{

    def getWorker() = { new Worker() }
    def getWorker(connection: Neold) = { new Worker(connection) }
    def getWorker(connection: Neold, namespace: Int) = { new Worker(connection, Some(namespace)) }
    def getWorker(connection: Neold, namespace: Int, logTick: Int) = {
        new Worker(connection, Some(namespace), logTick)
    }

    private class LimitedQueue[E](maxSize: Int) extends LinkedBlockingQueue[E](maxSize) {
        override def offer(e: E) : Boolean = {
            try {
                put(e)
                return true
            } catch {
                case e: InterruptedException => Thread.currentThread().interrupt();
            }
            false
        }
    }

}
class Worker (connection: Neold = Neold(),
              ns: Option[Int] = None,
              counterTick: Int = 100,
              mergerThreads: Int = Runtime.getRuntime.availableProcessors(),
              workerThreads: Int = 2,
              queueLength: Int = 100){

    private val workerPool = new ThreadPoolExecutor(1, workerThreads, 1L, TimeUnit.SECONDS, new LimitedQueue[Runnable](queueLength), new ThreadPoolExecutor.CallerRunsPolicy())
    private val mergerPool = new ThreadPoolExecutor(1, mergerThreads, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable](queueLength), new ThreadPoolExecutor.CallerRunsPolicy())

    //private val lock = new Semaphore(semaphoreNb, true)
    private var counter = 0

    def feed(raw: String, siteInfo: SiteInfo): Unit = {
        workerPool.execute(new Processor(raw, siteInfo))
    }

    def shutdown() = {
        workerPool.shutdown()
        mergerPool.shutdown()
    }

    def writeSiteInfo(siteInfo: SiteInfo) = {
        connection.executeImmediate1("MERGE (s:SiteInfo) SET s.pageCase = {pageCase}, s.namespaces = {namespaces}", Map(
            "pageCase" -> siteInfo.pageCase,
            "namespaces" -> :?{
                siteInfo.namespaces.foldLeft("{"){ (str: String, ns: (String, Int)) =>
                    str +
                    { if (str.length() != 1){ "," } else { "" } } +
                    "\"" + ns._2 + "\": \"" + ns._1 + "\""
                 } + "}"
            }
        )){ resultString: String =>
            val result = TransactionalAdapter.toEither(resultString)

            if(result.isLeft) {
                Console.err.println(s"[CYPHER ~ Failure] ${result.left.get}")
            }
        }
    }

    private class Processor(input: String, siteInfo: SiteInfo) extends Runnable{

        override def run(): Unit = {
            // For each <page> tag contents
            //  1) Parse it entirely using the scala xml parser
            //  2) Retrieve the contents of the <title> and <ns> tags
            //  3) Parse the <text> tag
            //      - Extract the contents of the [[ ]] tags
            //      - If the [[ ]] tag contains the character |, add to the edge list the substring starting from |
            //      - Else add the whole contents to the List
            //  4) Fill the PageNode object with the title & tag and send the Pair to the Joiner

            val xml_input : scala.xml.Elem = XML.loadString(input)
            val title = (xml_input \ "title").text
            val namespace = (xml_input \ "ns").text.toInt

            if(ns.exists{ ns => namespace != ns})
                return

            val text_contents = (xml_input \ "revision" \ "text").text

            val references = """\[\[.*?\]\]""".r.findAllIn(text_contents)

            val regex_alias = """\[\[.*?[|].*?\]\]""".r
            val regex_noalias = """\[\[.*?\]\]""".r

            val data = (new PageNode(title, namespace), references.toSet.map{
                reference : String => reference match {
                    case regex_alias()      => reference.substring(2, reference.indexOf('|'))
                    case regex_noalias()    => reference.substring(2, reference.length - 2)
                }
            }.filter{
                reference => /*!reference.contains(":") &&*/ reference.compareTo(title) != 0
            }.map{ reference =>
                if(reference.contains("#"))
                    reference.substring(0, reference.indexOf("#"))
                else
                    reference
            }.map{ reference =>
                if(reference.contains(":")){
                    val namespaceTitle = reference.substring(0, reference.indexOf(":"))
                    val title = reference.substring(reference.indexOf(":") + 1)
                    PageNode(title, siteInfo.namespaces.getOrElse(namespaceTitle, 0))
                } else {
                    PageNode(reference, 0)
                }
            })

            mergerPool.execute(new Merger(data))
        }

    }

    private class Merger(input : (PageNode, Set[PageNode]), retries: Int = 5) extends Runnable{

        override def run(): Unit = {
            // Create in the Neo4J DB the PageNode if not already existing
            // Create unique relations to every node contained in the list, creating the nodes in the process if they are not existing already in the DB

            //[Lock] Prevent Neo4J concurrency issues
            //lock.acquire()

            //Page creation if it does not already exist
            //On match, updates id & namespace values
            val creationRequest = "MERGE (p:PageNode {id: {id}}) SET p.title = {title}, p.namespace = {namespace}"

            val pageId = input._1.title+input._1.namespace
            val initStack = Stack((creationRequest, Map(
                "id" -> :?(pageId),
                "title" -> :?(input._1.title),
                "namespace" -> :!(input._1.namespace.toString)
            )))

            //Create & link the page to its neighbours
            val queries = input._2.foldLeft(initStack){ (queriesStack, neighbour) =>
                val query =  (
                    "MATCH (p:PageNode {id: {id}}) " +
                    "MERGE (neighbour: PageNode {id: {neighbourId}}) " +
                    "CREATE (p)-[:LINKS]->(neighbour)",
                    Map(
                        "id" -> :?(pageId),
                        "neighbourId" -> :?(neighbour.title+neighbour.namespace)
                    )
                )

                if(ns.exists{ ns => neighbour.namespace != ns})
                    queriesStack
                else
                    queriesStack :+ (query)
            }

            val request = connection.executeImmediate(queries :_*){  resultString: String =>

                //[Release] Prevent Neo4J concurrency issues
                //lock.release()

                val result = TransactionalAdapter.toEither(resultString)

                if(result.isLeft) {
                    if (retries > 0){
                        //Console.err.println(s"[CYPHER ~ Retry (${retries} tries left})] ${input._1}")
                        mergerPool.execute(new Merger(input, retries - 1))
                    } else {
                        Console.err.println(s"[CYPHER ~ Failure] ${input._1}")
                        //Console.err.println(result.left.get)
                        //Console.err.println(queries.toString())
                    }
                } else {
                    counter = counter + 1
                    if(counter % (counterTick) == 0)
                        println(s"(${new Date()}) [$counter] Pages merged.")
                }

            }
            request.onFailure{
                case f =>
                    println(f)
                    //lock.release()
            }

            Await.result(request, Duration.Inf)
        }

    }

}
