/*
 * Copyright 2018-2019 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core.data

import cats.Order
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.scalasteward.core.util
import scala.util.Try

final case class Version(value: String) {
  val numericComponents: List[BigInt] =
    value
      .split(Array('.', '-', '+'))
      .flatMap(util.string.splitNumericAndNonNumeric)
      .map {
        case "SNAP" | "SNAPSHOT" => BigInt(-3)
        case "M"                 => BigInt(-2)
        case "RC"                => BigInt(-1)
        case s                   => Try(BigInt(s)).getOrElse(BigInt(0))
      }
      .toList

  /** Selects the next version from a list of potentially newer versions.
    *
    * Implements the scheme described in this FAQ:
    * https://github.com/fthomas/scala-steward/blob/master/docs/faq.md#how-does-scala-steward-decide-what-version-it-is-updating-to
    */
  def selectNext(versions: List[Version]): Option[Version] = {
    val firstNegative = numericComponents.indexWhere(_ < 0)
    val cutoff = if (firstNegative < 0) numericComponents.size else firstNegative - 1

    versions
      .filter(_ > this)
      .groupBy(_.numericComponents.zip(numericComponents).take(cutoff).takeWhile {
        case (c1, c2) => c1 === c2
      })
      .toList
      .sortBy { case (commonPrefix, _) => commonPrefix.length }
      .lastOption
      .map { case (_, vs) => vs }
      .flatMap(_.lastOption)
  }
}

object Version {
  implicit val versionOrder: Order[Version] =
    Order.from[Version] { (v1, v2) =>
      val (c1, c2) = padToSameLength(v1.numericComponents, v2.numericComponents, BigInt(0))
      c1.compare(c2)
    }

  implicit val versionDecoder: Decoder[Version] =
    deriveDecoder

  implicit val versionEncoder: Encoder[Version] =
    deriveEncoder

  private def padToSameLength[A](l1: List[A], l2: List[A], elem: A): (List[A], List[A]) = {
    val maxLength = math.max(l1.length, l2.length)
    (l1.padTo(maxLength, elem), l2.padTo(maxLength, elem))
  }
}
