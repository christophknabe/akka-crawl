Christoph Knabe, 2018-08-05
# Akka Crawl #

Public Webpage Crawler as Demonstration for Usage of Akka Actors and Akka Streams

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

## Authors ##

* **Christoph Knabe** for the idea of the public webpage crawler as a demonstration of using Akka for scaling parallel web requests, for the single page access prototype with Spray and for the summary part. Also later for making it more robust.
* **Heiko Seeberger** for porting the Spray prototype to Akka Streams.
* **blazej0@github** for coworking in this solution at the Berlin 2014 Scala hackaton

## Documentation ##

### Usage ###

`sbt run` [_uri_]

This will configure the given URI or else [https://www.berlin.de/](https://www.berlin.de/) as the start point for crawling the web.
The program issues a prompt on the console. 

When you press &lt;ENTER&gt;, it will start. It scans the web page, notes all contained links therein as to scan, and finally prints the address of the completely scanned page, resulting in an endless web crawl, scan and print process. 

When you press &lt;ENTER&gt; again, the program will start a smooth shutdown process. At first it prints a statistics message about the scanned pages. Then it will print the addresses of all links it found, interspersed with those of newer scanned pages.

#### Troubleshooting ####

If the program does not find any web pages, probably the start page could not be found within the timeout duration. 
In order to verify this, you can set the configuration parameter `akka.loglevel` in file `application.conf` to `debug`.
Or you could try with another start URL. 
Or you could increase the value `akka-crawl.response-timeout` in file `resources/application.conf`!
There can be also limits for using the internet connection, e.g. if you are in a WiFi network.

### Collaboration ###

The `AkkaCrawlApp` sets up an `ActorSystem` with one `Crawler` actor.
Then it starts the `Crawler` by sending him a `ScanPage` message for the start URI.
The `Crawler` tries to get each page by a `Scanner` actor-per-request, lets it scan for URIs and send himself further `ScanPage` messages. A tried URI is stored in `triedUris` in order to avoid trying it again.
Each successfully scanned page gets registered by the `Crawler` actor into `scannedPages`.
On message `PrintScanSummary` it prints a summary of successful page scans, and changes to `smoothShutDown` behavior.

The priority of the `PrintScanSummary` message over `ScanPage` message types is achieved by an `UnboundedControlAwareMailbox` for the manager actor. 

In the `smoothShutDown` behavior the `Crawler` actor will print all unprocessed commands, and only when during 5 seconds no more commands arrive, it will terminate the actor system.  

`Crawler` uses a Reactive Stream when scanning a web page, as the page could be very long. This occurs in method `Crawler.receive` in the first `case` branch by `entity.dataBytes`.

See more explanations in the [Presentation Slides](src/doc/discussion.pdf).

### Example Crawl Results ###

| Environment          | Scanned in 1 minute           | Unprocessed commands |
| -------------------- | ---------------------------- | --------------------- |
| At home (DSL)        | 318 pages                    | 11                    |
| In university WLAN   | 2,747 pages                  | 387                   |
| On university server | 7,779 pages                  | 797,368               |



## TODO ##

* Throttle the crawling, when the average scan times get longer and longer (more than 5 seconds)
* Find out why scanning an individual page at home lasts longer and longer after about a minute of crawling. For example the first 10 pages need about 1 to 2 seconds each, whereas pages 350 to 359 need about 23 to 50 seconds each.
* Find out how to find the limiting factor (CPU, network, open ports, JVM-RAM, ...)
* Find out how to use a purely streamed solution with backpressure.


