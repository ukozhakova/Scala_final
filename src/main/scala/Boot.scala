import java.util.concurrent.{Future, TimeUnit}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.stream.javadsl.StreamConverters
import akka.util.Timeout
import org.slf4j.LoggerFactory
import response.Response
import service.PhotoService

import scala.concurrent.duration._
import scala.util.{Failure, Success}
//import java.time.Duration

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.s3.model.{CreateBucketRequest, GetBucketLocationRequest}

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn


object Boot extends App with JsonSupport {
  implicit val system: ActorSystem = ActorSystem("final-exam-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val log = LoggerFactory.getLogger("Boot")

  // needed fot akka's ASK pattern
  implicit val timeout: Timeout = Timeout(60.seconds)
  val clientRegion: Regions = Regions.EU_CENTRAL_1
  val bucketName = "final-ulbo-bucket-1234"
  val token = "1039539897:AAFrRXCZf3VSf9jClVWqHhnl5BQBDdc3yPY" // config.getString("telegram.token") // token
  log.info(s"Token: $token")

  val chatID = -352088280;
//  val token = "1005517677:AAF53-ZxndoF75E1DBGEqNAIbMsLiWe0BPs" // config.getString("telegram.token") // token
//  log.info(s"Token: $token")
//
//  var chatID: Long = -1001383728198L;

  // amazon credentials
  val awsCreds = new BasicAWSCredentials("AKIARTZMNRRGSIE6RKUA", "QzgPck0gRJ5yB+fr13W4mRGOCpyezXgq88albzae")

  val s3Client: AmazonS3 =AmazonS3ClientBuilder.standard()
    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
    .withRegion(clientRegion)
    .build()

  val photoService: ActorRef = system.actorOf(PhotoService.props(s3Client, bucketName), "kbtu-photo-service")
  createBucket(s3Client, bucketName)
  val route: Route = {
    pathPrefix("photo" / Segment) { userId =>
      concat(
        post {
          // example: POST localhost:8081/photo/user-12
          fileUpload("photoUpload") {
            case (fileInfo, fileStream) =>

              // Photo upload
              // fileInfo -- information about file, including FILENAME
              // filestream -- stream data of file

              val inputStream = fileStream.runWith(StreamConverters.asInputStream(5.seconds))
              complete {
                sendMessageToBot(s"Successfully uploaded photo with key: ${fileInfo.fileName}")
                (photoService ? PhotoService.UploadPhoto(inputStream, userId, fileInfo.fileName)).mapTo[Either[Response.Error, Response.Accepted]]
              }
          }
        },
        path(Segment) { fileName =>
          concat(
            get {
              complete {
                sendMessageToBot(fileName)
                (photoService ? PhotoService.GetPhoto(userId, fileName)).mapTo[Either[Response.Error, Response.PhotoUrl]]
              }
            },
            delete{
              complete{
                sendMessageToBot(s"$fileName is going to be deleted")
                (photoService ? PhotoService.DeletePhoto(userId, fileName)).mapTo[Either[Response.Error, Response.Accepted]]
              }
            }
          )
        }
      )
    }
  }

  def createBucket(s3client: AmazonS3, bucket: String): Unit = {
    if (!s3client.doesBucketExistV2(bucket)) {
      s3client.createBucket(bucket)
      log.info(s"Bucket with name: $bucket created")
    } else {
      log.info(s"Bucket $bucket already exists")
    }
  }

  def sendMessageToBot(msg: String): Unit = {
    val message: TelegramMessage = TelegramMessage(chatID, msg);

    val httpReq = Marshal(message).to[RequestEntity].flatMap { entity =>
      val request = HttpRequest(HttpMethods.POST, s"https://api.telegram.org/bot$token/sendMessage", Nil, entity)
      log.debug("Request: {}", request)
      Http().singleRequest(request)
    }

    httpReq.onComplete {
      case Success(value) =>
        log.info(s"Response: $value")
        value.discardEntityBytes()

      case Failure(exception) =>
        log.error(exception.getMessage)
    }
  }
  val bindingFuture = Http().bindAndHandle(route, "localhost", 8081)
  println(s"Server online at http://localhost:8081/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ ⇒ system.terminate()) // and shutdown when done
}