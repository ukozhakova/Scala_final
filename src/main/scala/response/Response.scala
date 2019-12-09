package response

sealed trait Response {
  def status: Int
}

object Response {
  case class Accepted(status: Int, message: String) extends Response
  case class PhotoUrl(status: Int, url: String) extends Response
  case class Error(status: Int, message: String) extends Response
}