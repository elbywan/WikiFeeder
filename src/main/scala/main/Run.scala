package main

import java.util.Date
import com.elbywan.scalargs.core.Scalargs._
import org.neold.core.Neold

import parsing.{Worker, XmlLoader}

object Run extends App{

    override def main(args: Array[String]): Unit = {
        val args_conf = program("WikiFeeder")
            .title("- Wikipedia Feeder")
            .usage("Parses an XML wikipedia dump and feeds it to a Neo4j database.")
            .arguments(
                argument("filepath")
                    .mandatory
                    .short("f")
                    .description("XML wikipedia dump file path"),
                argument("namespace")
                    .as[Int]{_.toInt}
                    .short("ns")
                    .description("Restrict the feeder to a given wikipedia namespace "),
                argument("neo4j-host")
                    .short("neohost")
                    .description("Neo4j host url."),
                argument("neo4j-port")
                    .as[Int]{_.toInt}
                    .short("neoport")
                    .description("Neo4j port."),
                argument("neo4j-username")
                    .short("neouser")
                    .description("Neo4j username."),
                argument("neo4j-password")
                    .short("neopass")
                    .description("Neo4j password."),
                argument("neo4j-secure")
                    .empty
                    .short("neosecure")
                    .description("Add this argument if the neo4j connection is secured (ssl)."),
                argument("log-tick")
                    .as[Int]{_.toInt}
                    .short("tick")
                    .description("Writes periodically (modulo this argument) the n° of pages parsed."),
                argument("queue-length")
                    .as[Int]{_.toInt}
                    .description("Worker & Merger queues length."),
                argument("xml-threads")
                    .as[Int]{_.toInt}
                    .description("Number of xml parsing threads."),
                argument("neo4j-threads")
                    .as[Int]{_.toInt}
                    .description("Number of simultaneous neo4j requests.")
            )

        val config = args_conf from args
        println(s"[Starting] ${new Date()}")

        val connection = Neold(
            host = config.getOrElse("neo4j-host", "localhost").asInstanceOf[String],
            port = config.getOrElse("neo4j-port", 7474).asInstanceOf[Int],
            username = config.getOrElse("neo4j-username", "").asInstanceOf[String],
            password = config.getOrElse("neo4j-password", "").asInstanceOf[String],
            secure = config.contains("neo4j-secure")
        )

        val worker = new Worker(connection = connection,
            ns = config.get("namespace").asInstanceOf[Option[Int]],
            counterTick = config.getOrElse("log-tick", 100).asInstanceOf[Int],
            queueLength = config.getOrElse("queue-length", 100).asInstanceOf[Int],
            workerThreads = config.getOrElse("xml-threads", 2).asInstanceOf[Int],
            mergerThreads = config.getOrElse("neo4j-threads", Runtime.getRuntime.availableProcessors()).asInstanceOf[Int]
        )

        XmlLoader.loadFromXML(
            config.getOrElse("filepath", "").asInstanceOf[String],
            worker
        )

    }


}
