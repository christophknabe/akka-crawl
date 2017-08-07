# Akka Crawl #

Public Webpage Crawler as Demonstration for Usage of Akka Actors and Akka Streams

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

## Authors ##

* Christoph Knabe for the idea of the public webpage crawler as a demonstration of using Akka for scaling parallel web requests, for the single page access prototype with Spray and for the statistics part
* Heiko Seeberger for porting the Spray prototype to Akka Streams.
* blazej0@github for coworking in this solution at the Berlin 2014 Scala hackaton

## Documentation ##

### Usage ###

`sbt run` _uri_

This will configure the given URI as the start point for crawling the web.
The program issues a prompt on the console. Once you press &lt;ENTER&gt;, it will crawl web pages and print all successfully scanned ones.

When you press &lt;ENTER&gt; again, the program will stop and print a statistics message.

#### Troubleshooting ####

If the program does not find any web pages, probably the start page could not be found within the timeout duration. 
In order to verify this, you can set the configuration parameter `akka.loglevel` in file `application.conf` to `debug`.
Or you could try with another start URL. 
Or you could increase the value `akka-crawl.response-timeout` in file `resources/application.conf`!
There can be also limits for using the internet connection, e.g. if you are in a WiFi network.

### Collaboration ###

The `AkkaCrawlApp` sets up an `ActorSystem` with a `CrawlerManager` actor.
Then it starts the `CrawlerManager` by sending him a `ScanPage` message for the start URI.
The `CrawlerManager` tries to get each page by a `Crawler` actor-per-request, lets it scan for URIs and send himself further `ScanPage` messages.
Each successfully scanned page gets registered by the `CrawlerManager` actor into its `archive`.
On message `PrintFinalStatistics` it will do so immediately and terminate the actor system. 
The priority of `PrintFinalStatistics` over other message types is achieved by an `UnboundedControlAwareMailbox` for the manager actor. 

A Reactive Stream is used when scanning a web page, as the page could be very long. This occurs in method `Crawler.receive` in the first `case` branch by `entity.dataBytes`.

## TODO ##

* Throttle the crawling, when the average scan times get longer and longer (more than 5 seconds)
* Report how many `ScanPage` commands were in the mailbox of the `CrawlerManager` actor, when it received the `PrintFinalStatistics` command.
* Use framing according to http://doc.akka.io/docs/akka-http/10.0.9/scala/http/implications-of-streaming-http-entity.html#consuming-the-http-response-entity-client- in order to split the response stream at each `href=`


