import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import response.Response
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val acceptedFormat = jsonFormat2(Response.Accepted)
  implicit val photoUrlFormat = jsonFormat2(Response.PhotoUrl)
  implicit val errorFormat = jsonFormat2(Response.Error)
  implicit val messageFormat: RootJsonFormat[TelegramMessage] = jsonFormat2(TelegramMessage)

}