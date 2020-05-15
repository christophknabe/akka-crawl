package de.bht_berlin.knabe.akkacrawl

import akka.actor.{ Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionKey }

import scala.concurrent.duration.{ Duration, FiniteDuration, MILLISECONDS }

//object Settings extends ExtensionKey[Settings]

class Settings(system: /*Extended*/ ActorSystem) /*extends Extension*/ {

  val responseTimeout: FiniteDuration = duration("response-timeout")

  private lazy val config = system.settings.config.getConfig("akka-crawl")

  private def duration(key: String): FiniteDuration = Duration(config.getDuration(key, MILLISECONDS), MILLISECONDS)

}
