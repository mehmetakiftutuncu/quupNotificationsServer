package controllers

import models.enum.NotificationTypes
import models.{Authentication, Notifications}
import play.api.Logger
import play.api.http.MimeTypes
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Controller, Request}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {
  def index: Action[AnyContent] = Action {
    Ok("Welcome to quup Notifications Server!")
  }

  def getNotifications: Action[JsValue] = QuupAction {
    implicit qr: QuupRequest =>
      Notifications.get(NotificationTypes.Comment) map {
        notificationList =>
          val json: JsObject = Json.obj(
            "notifications" -> Json.toJson(notificationList.map(_.toJson))
          )

          Ok(json).as(MimeTypes.JSON)
      }
  }

  def getMentions: Action[JsValue] = QuupAction {
    implicit qr: QuupRequest =>
      Notifications.get(NotificationTypes.Mention) map {
        notificationList =>
          val json: JsObject = Json.obj(
            "notifications" -> Json.toJson(notificationList.map(_.toJson))
          )

          Ok(json).as(MimeTypes.JSON)
      }
  }

  def getMessages: Action[JsValue] = QuupAction {
    implicit qr: QuupRequest =>
      Notifications.get(NotificationTypes.DirectMessage) map {
        notificationList =>
          val json: JsObject = Json.obj(
            "notifications" -> Json.toJson(notificationList.map(_.toJson))
          )

          Ok(json).as(MimeTypes.JSON)
      }
  }

  def login: Action[JsValue] = Action.async(parse.json) {
    request: Request[JsValue] =>
      val usernameAsOpt: Option[String] = (request.body \ "username").asOpt[String]
      val passwordAsOpt: Option[String] = (request.body \ "password").asOpt[String]

      if (usernameAsOpt.getOrElse("").isEmpty) {
        Logger.error(s"Failed to login, username is empty!")
        Future.successful(InternalServerError)
      } else if (passwordAsOpt.getOrElse("").isEmpty) {
        Logger.error(s"Failed to login, password is empty!")
        Future.successful(InternalServerError)
      } else {
        val username: String = usernameAsOpt.get
        val password: String = passwordAsOpt.get

        Authentication.getTrackingCookie flatMap {
          trackingCookieAsOpt: Option[String] =>
            if (trackingCookieAsOpt.isEmpty) {
              Future.successful(InternalServerError)
            } else {
              Authentication.getSessionCookie(trackingCookieAsOpt.get, username, password) map {
                sessionCookieAsOpt: Option[String] =>
                  if (sessionCookieAsOpt.isEmpty) {
                    InternalServerError
                  } else {
                    Ok(Json.obj("tracking" -> trackingCookieAsOpt.get, "session"  -> sessionCookieAsOpt.get)).as(MimeTypes.JSON)
                  }
              }
            }
        }
      }
  }

  def logout: Action[JsValue] = QuupAction {
    implicit qr: QuupRequest =>
      Authentication.logout map {
        successful: Boolean =>
          if (!successful) {
            InternalServerError
          } else {
            Ok
          }
      }
  }
}