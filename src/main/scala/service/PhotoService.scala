package service

import java.io.{ByteArrayInputStream, InputStream}

import akka.actor.{Actor, ActorLogging, Props}
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest}
import com.amazonaws.util.IOUtils
import response.Response

object PhotoService {
  case class UploadPhoto(inputStream: InputStream, userId: String, fileName: String)
  case class GetPhoto(userId: String, fineName: String)
  case class DeletePhoto(userId: String, fineName: String)
  def props(s3Client: AmazonS3, bucketName: String) = Props(new PhotoService(s3Client, bucketName))
}

class PhotoService(s3Client: AmazonS3, bucketName: String) extends Actor with ActorLogging {
  import PhotoService._

  override def receive: Receive = {

    case GetPhoto(userId, fileName) =>
      // Here we generate pre-signed URL to photo

      // Set the presigned URL to expire after one hour.
      val expiration = new java.util.Date()
      // 1000 * 60 * 60 is ONE hour in milliseconds
      val inOneHour = expiration.getTime + 1000 * 60 * 60
      expiration.setTime(inOneHour)

      val objectName = s"$userId/$fileName"
      // check if object exists in AWS first
      if (s3Client.doesObjectExist(bucketName, objectName)) {
        val url: String = s3Client.generatePresignedUrl(bucketName, objectName, expiration).toString
        log.info("Generated a presigned URL: {}", url)
        // TODO: respond with PhotoUrl where status code is 200 and url is `url`
        sender() ! Right(Response.PhotoUrl(200, url))
      } else {
        log.info("Failed to get photo for userId: {}, fileName: {}. Responding with status 404.", userId, fileName)

        // TODO: respond with Error where status code is 404 and message is `Photo not found`
        sender() ! Left(Response.Error(404, "Photo not found"))
      }

    case UploadPhoto(inputStream, userId, fileName) =>
    // TODO: implement this functionality
    // photo object's fullPath inside bucket must be `userId/photoFileName`
    // example: userId = user-2, fileName = photo.jpg => object key inside bucket must be `user-2/photo.jpg`
      val key = s"$userId/$fileName"
      log.info(s"Putting photo with key: $key to S3")
      val metadata = new ObjectMetadata()
      metadata.setContentType(fileName)
      val putObjectRequest = new PutObjectRequest(bucketName, key, inputStream, metadata)

      if (s3Client.doesObjectExist(bucketName, key)){
        log.error(s"File with key $key already exists in S3")
        sender() ! Left(Response.Error(409, "Such file already exists"))
      } else {
        s3Client.putObject(putObjectRequest)
        log.info(s"Successfully uploaded photo with key: $key to S3")
        sender() ! Right(Response.Accepted(200, "OK"))
      }


    // TODO: check that file exists or not
    // If such file exists => respond with Error where status is 409 and message is `Such file already exists`
    // Otherwise => respond with Accepted where status is 200 and message is `OK`

    case DeletePhoto(userId, fileName) =>
    // TODO: implement this functionality
    // Check if such object exists on AWS
    // If exists => respond with Accepted where status code is 200 and message is `OK`
    // If does not exist => respond with Error where status code is 404 and message is `Photo not found`
      val key = s"$userId/$fileName"
      log.info(s"Deleting file with key $key")

      if (s3Client.doesObjectExist(bucketName, key)){
        s3Client.deleteObject(bucketName, key)
        log.info(s"Photo with key $key successfully deleted")
        sender() ! Right(Response.Accepted(200, "OK"))
      } else {
        log.error("Photo does not exist")
        sender() ! Left(Response.Error(404, "Photo not found"))
      }

    // photo object's fullPath is the same as in previous methods (GetPhoto and UploadPhoto)

  }
}