package tipsy.db


import io.circe.generic.JsonCodec

/**
  * Includes case classes for expected data bodies in web requests
  */
object Requests {
  @JsonCodec case class ProgramInsertReq (
    id: Option[Int],
    userId: String,
    quesId: String,
    code: String,
    file: Option[String],
    score: Option[String],
    updateClusters: Option[Boolean]
  )
}
