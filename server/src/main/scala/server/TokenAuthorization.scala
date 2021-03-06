package server

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{complete, optionalHeaderValueByName, provide}
import org.json4s.native.JsonMethods.parse
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtOptions}

import java.util.concurrent.TimeUnit
import scala.util.parsing.json.JSONObject
import scala.util.{Failure, Success}

object TokenAuthorization {
    private val secretKey = "super_secret_key"
    private val tokenExpiryPeriodInDays = 365

    def isValid(jwtToken: String): Boolean = Jwt.isValid(jwtToken, secretKey, Seq(JwtAlgorithm.HS256), JwtOptions())
    def isExpired(jwtToken: String): Boolean = Jwt.isValid(jwtToken, secretKey, Seq(JwtAlgorithm.HS256), JwtOptions(expiration = false))

    def generateToken(userid: Int): String = {
        val claims = JwtClaim(
            JSONObject(Map(
                "userid" -> userid,
                "expiredAt" -> (System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(tokenExpiryPeriodInDays))))
                    .toString())
        val token = Jwt.encode(claims, secretKey, JwtAlgorithm.HS256)
        println("Generating new token for: ", userid, token)
        token
    }

    def authenticated: Directive1[Map[String, Any]] = {
        optionalHeaderValueByName("Authorization").flatMap { tokenFromUser =>
            val jwtToken = tokenFromUser.get.split(" ")
            jwtToken(1) match {
                case jwtToken if isValid(jwtToken) => {
                    val claims = getClaims(jwtToken)
                    if (claims.contains("userid")) { provide(claims) }
                    else { complete(StatusCodes.Unauthorized -> "Invalid Token claims.") }
                }
                case jwtToken if isExpired(jwtToken) => complete(StatusCodes.Unauthorized -> "Session expired.")
                case _ =>  complete(StatusCodes.Unauthorized -> "Invalid Token")
            }
        }
    }

    private def getClaims(jwtToken: String): Map[String, String] =
        Jwt.decodeRaw(jwtToken, secretKey, Seq(JwtAlgorithm.HS256)) match {
            case Success(value) => parse(value).values.asInstanceOf[Map[String, String]]
            case Failure(_) => Map.empty[String, String]
        }
}