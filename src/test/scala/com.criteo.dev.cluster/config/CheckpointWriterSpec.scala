package com.criteo.dev.cluster.config

import java.time.Instant

import com.typesafe.config.ConfigRenderOptions
import org.scalatest.{FlatSpec, Matchers}

class CheckpointWriterSpec extends FlatSpec with Matchers {
  "apply" should "convert a Checkpoint to a Config" in {
    val checkpoint = Checkpoint(
      Instant.ofEpochMilli(1000),
      Instant.ofEpochMilli(2000),
      Set("db.t1"),
      Set("db.t2"),
      Set("db.t3")
    )
    val res = CheckpointWriter(checkpoint).root.render(ConfigRenderOptions.concise)
    res shouldEqual """{"created":1000,"failed":["db.t3"],"finished":["db.t2"],"todo":["db.t1"],"updated":2000}"""
  }
}
