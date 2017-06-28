# Akka Crawl #

Public Webpage Crawler as Demonstration for Usage of Akka Actors and Akka Streams

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

## Authors ##

* Christoph Knabe for the idea of the public webpage crawler as a demonstration of using Akka for scaling parallel web requests, for the original prototype with Spray and for the statistics part
* Heiko Seeberger for porting the Spray prototype to Akka Streams.
* blazej0@github for coworking in this solution at the Berlin 2014 Scala hackaton

## Documentation ##

### Usage ###

`sbt run` _url_

This will configure the given URL as the start point for crawling the web.
The program issues a prompt on the console. Once you press &lt;ENTER&gt;, it will crawl web pages and log all successfully scanned ones.

When you press &lt;ENTER&gt; again, the program will stop and print a statistics message.

#### Troubleshooting ####

If the program does not find any web pages, probably the start page could not be found within the timeout duration. 
In order to verify this, you can set the configuration parameter `akka.loglevel` in file `application.conf` to `debug`.
Or you could try with another start URL. There can be also limits for using the internet connection, e.g. if you are in a WiFi network.

### Collaboration ###

The `AkkaCrawlApp` sets up an `ActorSystem` with a `crawlerManager` and a `statsCollector`.
Then it starts the `crawlerManager` by sending him a `ScanPage` message for the start URL.
The `crawlerManager` tries to get each page by an actor per request, scans it for URLs and sends himself further `ScanPage` messages.
Each successfully scanned page gets registered by the `statsCollector` actor.
On message `PrintFinalStatistics` it will do so and terminate the actor system.

A Reactive Stream is used when scanning a web page, as it could be very long. This occurs in method `Crawler.getting` by `entity.dataBytes`.

## TODO ##

Split the `CrawlerManager` actor into two actors: one for doing the requests, and another for collecting data for statistics.


