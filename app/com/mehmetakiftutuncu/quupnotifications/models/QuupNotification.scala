package com.mehmetakiftutuncu.quupnotifications.models

import com.github.mehmetakiftutuncu.errors.{CommonError, Errors}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.util.Try
import scala.util.matching.Regex

/**
  * Created by akif on 30/06/16.
  */
case class QuupNotification(quupId: String,
                            notificationType: NotificationType,
                            when: Long,
                            by: QuupUser,
                            others: Set[QuupUser] = Set.empty[QuupUser]) {
  val quupUrl: String = s"""/social/entry/$quupId"""

  def switchByTo(quupUser: QuupUser): QuupNotification = {
    if (quupUser.userId == by.userId) {
      this
    } else {
      copy(by = quupUser, others = others + by)
    }
  }

  def toJson: JsObject = {
    Json.obj(
      "quupId" -> quupId,
      "type"   -> notificationType.toString,
      "when"   -> when,
      "by"     -> by.toJson,
      "others" -> others.map(_.toJson)
    )
  }

  override def toString: String = toJson.toString()
}

object QuupNotification {
  def getQuupNotifications(json: JsValue): MaybeValue[List[QuupNotification]] = {
    val maybeDataJson: Option[JsValue] = (json \ "Data").toOption

    if (maybeDataJson.isEmpty) {
      Errors(CommonError.invalidData.reason("""Failed to parse QuupNotification list because "Data" key for is missing!""").data(json.toString()))
    } else {
      val maybeDataArray: Option[JsArray] = maybeDataJson.get.asOpt[JsArray]

      if (maybeDataArray.isEmpty) {
        Errors(CommonError.invalidData.reason("""Failed to parse QuupNotification list because value of "Data" wasn't a JsArray!""").data(json.toString()))
      } else {
        val quupNotificationsJsonList: List[JsValue] = maybeDataArray.get.value.toList

        val (errors: Errors, quupNotifications: List[QuupNotification]) = quupNotificationsJsonList.foldLeft(Errors.empty -> List.empty[QuupNotification]) {
          case ((errors: Errors, quupNotifications: List[QuupNotification]), quupNotificationJson: JsValue) =>
            val maybeQuupNotification: MaybeValue[QuupNotification] = getQuupNotification(quupNotificationJson)

            val newErrors: Errors                            = errors ++ maybeQuupNotification.maybeErrors.getOrElse(Errors.empty)
            val newQuupNotifications: List[QuupNotification] = maybeQuupNotification.maybeValue.map(quupNotifications :+ _).getOrElse(quupNotifications)

            newErrors -> newQuupNotifications
        }

        if (errors.hasErrors) {
          errors
        } else {
          quupNotifications
        }
      }
    }
  }

  private def getQuupNotification(json: JsValue): MaybeValue[QuupNotification] = {
    val maybeNJson: Option[JsValue] = (json \ "n").toOption

    if (maybeNJson.isEmpty) {
      Errors(CommonError.invalidData.reason("""Failed to parse QuupNotification because "n" key for is missing!""").data(json.toString()))
    } else {
      val maybeNArray: Option[JsArray] = maybeNJson.get.asOpt[JsArray]

      if (maybeNArray.isEmpty) {
        Errors(CommonError.invalidData.reason("""Failed to parse QuupNotification because value of "n" wasn't a JsArray!""").data(json.toString()))
      } else {
        val quupNotificationsJsonList: List[JsValue] = maybeNArray.get.value.reverse.toList

        val (errors: Errors, maybeQuupNotification: Option[QuupNotification]) = quupNotificationsJsonList.foldLeft(Errors.empty -> Option.empty[QuupNotification]) {
          case ((errors: Errors, currentQuupNotification: Option[QuupNotification]), quupNotificationJson: JsValue) =>
            val maybeQuupNotification: MaybeValue[QuupNotification] = from(quupNotificationJson)

            val newErrors: Errors = errors ++ maybeQuupNotification.maybeErrors.getOrElse(Errors.empty)

            val newCurrentQuupNotification: Option[QuupNotification] = currentQuupNotification flatMap {
              quupNotification: QuupNotification =>
                maybeQuupNotification.maybeValue map {
                  newQuupNotification: QuupNotification =>
                    quupNotification.switchByTo(newQuupNotification.by)
                }
            } orElse {
              maybeQuupNotification.maybeValue
            }

            newErrors -> newCurrentQuupNotification
        }

        if (errors.hasErrors) {
          errors
        } else {
          maybeQuupNotification.get
        }
      }
    }
  }

  private def from(json: JsValue): MaybeValue[QuupNotification] = {
    try {
      val maybeQuupIdJson: Option[JsValue]      = (json \ "ei").toOption
      val maybeDescriptionJson: Option[JsValue] = (json \ "dt").toOption
      val maybeWhenJson: Option[JsValue]        = (json \ "cs").toOption

      val (maybeQuupId: Option[String], quupIdErrors: Errors) = if (maybeQuupIdJson.isEmpty) {
        None -> Errors(CommonError.invalidData.reason("""Failed to parse quupId of QuupNotification because "ei" key for is missing!""").data(json.toString()))
      } else {
        val maybeQuupId: Option[String] = maybeQuupIdJson.get.asOpt[String]

        if (maybeQuupId.isEmpty) {
          None -> Errors(CommonError.invalidData.reason("""Failed to parse quupId of QuupNotification because value of "ei" wasn't a String!""").data(maybeQuupIdJson.get.toString()))
        } else {
          maybeQuupId -> Errors.empty
        }
      }

      val (maybeNotificationType: Option[NotificationType], notificationTypeErrors: Errors) = if (maybeDescriptionJson.isEmpty) {
        None -> Errors(CommonError.invalidData.reason("""Failed to parse notificationType of QuupNotification because "dt" key for is missing!""").data(json.toString()))
      } else {
        val maybeDescription: Option[String] = maybeDescriptionJson.get.asOpt[String]

        if (maybeDescription.isEmpty) {
          None -> Errors(CommonError.invalidData.reason("""Failed to parse notificationType of QuupNotification because value of "dt" wasn't a String!""").data(maybeDescriptionJson.get.toString()))
        } else {
          val maybeNotificationType: MaybeValue[NotificationType] = NotificationTypes.from(maybeDescription.get)

          maybeNotificationType.maybeValue -> maybeNotificationType.maybeErrors.getOrElse(Errors.empty)
        }
      }

      val (maybeWhen: Option[Long], whenErrors: Errors) = if (maybeWhenJson.isEmpty) {
        None -> Errors(CommonError.invalidData.reason("""Failed to parse when of QuupNotification because "cs" key for is missing!""").data(json.toString()))
      } else {
        val maybeWhen: Option[Long] = maybeWhenJson.get.asOpt[String] flatMap {
          whenString: String =>
            Try.apply(whenString.toLong).toOption
        }

        if (maybeWhen.isEmpty) {
          None -> Errors(CommonError.invalidData.reason("""Failed to parse when of QuupNotification because value of "cs" wasn't a Long!""").data(maybeWhenJson.get.toString()))
        } else {
          maybeWhen -> Errors.empty
        }
      }

      val maybeBy: MaybeValue[QuupUser] = QuupUser.from(json)
      val byErrors: Errors              = maybeBy.maybeErrors.getOrElse(Errors.empty)

      val errors: Errors = quupIdErrors ++ notificationTypeErrors ++ whenErrors ++ byErrors

      if (errors.hasErrors) {
        // Something went wrong
        errors
      } else {
        // Everything was fine
        val quupId: String                     = maybeQuupId.get
        val notificationType: NotificationType = maybeNotificationType.get
        val when: Long                         = maybeWhen.get
        val by: QuupUser                       = maybeBy.value

        QuupNotification(quupId, notificationType, when, by)
      }
    } catch {
      case t: Throwable =>
        Errors(CommonError.invalidData.reason("Failed to parse QuupNotification!").data(json.toString()))
    }
  }
}

sealed trait NotificationType {
  val regex: Regex
}

object NotificationTypes {
  case object QuupLike    extends NotificationType {override val regex: Regex = """.+gönderini.+beğendi.*""".r}
  case object CommentLike extends NotificationType {override val regex: Regex = """.+yorumunu.+beğendi.*""".r}
  case object Comment     extends NotificationType {override val regex: Regex = """.+yorum.+yaptı.*""".r}
  case object Mention     extends NotificationType {override val regex: Regex = """.+senden.+bahsetti.*""".r}
  case object Message     extends NotificationType {override val regex: Regex = """.+sana.+gönderdi.*""".r}
  case object Follow      extends NotificationType {override val regex: Regex = """.+takip.*""".r}
  case object Share       extends NotificationType {override val regex: Regex = """.+paylaştı.*""".r}

  private val types: Set[NotificationType] = Set(QuupLike, CommentLike, Comment, Mention, Message, Follow, Share)

  def from(description: String): MaybeValue[NotificationType] = {
    val maybeNotificationType: Option[NotificationType] = types.find(_.regex.findFirstMatchIn(description).isDefined)

    if (maybeNotificationType.isEmpty) {
      Errors(CommonError.invalidData.reason("""Failed to get a NotificationType from description because it didn't match to any!""").data(description))
    } else {
      maybeNotificationType.get
    }
  }
}
