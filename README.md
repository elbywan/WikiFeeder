WikiFeeder - Wikipedia Neo4j Feeder
-----------------------------

##Build

Package the jar with `sbt assembly` (produces an executable jar located in `target/scala-2.11/`), then run it with `scala`.

*or*

Run it directly from the command line with `sbt "run [ARGUMENTS]"`

##Usage

```
Parses an XML wikipedia dump and feeds it to a Neo4j database.

Arguments :

= Mandatory =

--filepath -f <value of type [String]>
	XML wikipedia dump file path

 = Optional =

--namespace -ns <value of type [Int]>
	Restrict the feeder to a given wikipedia namespace
--neo4j-host -neohost <value of type [String]>
	Neo4j host url.
--neo4j-port -neoport <value of type [Int]>
	Neo4j port.
--neo4j-username -neouser <value of type [String]>
	Neo4j username.
--neo4j-password -neopass <value of type [String]>
	Neo4j password.
--neo4j-secure -neosecure
	Add this argument if the neo4j connection is secured (ssl).
--log-tick -tick <value of type [Int]>
	Writes periodically (modulo this argument) the n° of pages parsed.
--queue-length <value of type [Int]>
	Worker & Merger queues length.
--xml-threads <value of type [Int]>
	Number of xml parsing threads.
--neo4j-threads <value of type [Int]>
	Number of simultaneous neo4j requests.
```

##Libs

- [Neold](https://github.com/elbywan/neold) -> Neo4j rest client
- [xtended-xml](https://github.com/elbywan/xtended-xml) -> Xml pull parser
- [Scalargs](https://github.com/elbywan/Scalargs) -> CLI arguments parsing

##TODO

- Code refactoring
- Multiple inputs (xml/url/other?)
- Database abstraction (neo4j/sql/other?)
