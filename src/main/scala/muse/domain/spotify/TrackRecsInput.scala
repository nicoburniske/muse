package muse.domain.spotify

import scala.collection.mutable

case class TrackRecsInput(
    // Max of 5 seed values.
    seedArtists: List[String],
    seedGenres: List[String],
    seedTracks: List[String],
    // Max is 100.
    limit: Int,
    market: Option[String],
    // Scalar configs.
    maxAcousticness: Option[Double],
    minAcousticness: Option[Double],
    maxDancability: Option[Double],
    minDancability: Option[Double],
    maxDurationMs: Option[Int],
    minDurationMs: Option[Int],
    maxEnergy: Option[Double],
    minEnergy: Option[Double],
    maxInstrumentalness: Option[Double],
    minInstrumentalness: Option[Double],
    maxKey: Option[Int],
    minKey: Option[Int],
    maxLiveness: Option[Double],
    minLiveness: Option[Double],
    maxLoudness: Option[Double],
    minLoudness: Option[Double],
    maxMode: Option[Int],
    minMode: Option[Int],
    maxPopularity: Option[Int],
    minPopularity: Option[Int],
    maxSpeechiness: Option[Double],
    minSpeechiness: Option[Double],
    maxTempo: Option[Double],
    minTempo: Option[Double],
    maxTimeSignature: Option[Int],
    minTimeSignature: Option[Int],
    maxValence: Option[Double],
    minValence: Option[Double],
    targetAcousticness: Option[Double],
    targetDancability: Option[Double],
    targetDurationMs: Option[Int],
    targetEnergy: Option[Double],
    targetInstrumentalness: Option[Double],
    targetKey: Option[Int],
    targetLiveness: Option[Double],
    targetLoudness: Option[Double],
    targetPopularity: Option[Int],
    targetMode: Option[Int],
    targetSpeechiness: Option[Double],
    targetTempo: Option[Double],
    targetTimeSignature: Option[Int],
    targetValence: Option[Double]
) {
  // This is why I love co-pilot.
  lazy val toUriString: String = {
    val sb = new mutable.StringBuilder
    sb.append("limit=" + limit)
    sb.append("&market=" + market)
    if (seedArtists.nonEmpty) {
      sb.append("&seed_artists=" + seedArtists.mkString(","))
    }
    if (seedGenres.nonEmpty) {
      sb.append("&seed_genres=" + seedGenres.mkString(","))
    }
    if (seedTracks.nonEmpty) {
      sb.append("&seed_tracks=" + seedTracks.mkString(","))
    }
    if (maxAcousticness.isDefined) {
      sb.append("&max_acousticness=" + maxAcousticness.get)
    }
    if (minAcousticness.isDefined) {
      sb.append("&min_acousticness=" + minAcousticness.get)
    }
    if (maxDancability.isDefined) {
      sb.append("&max_dancability=" + maxDancability.get)
    }
    if (minDancability.isDefined) {
      sb.append("&min_dancability=" + minDancability.get)
    }
    if (maxDurationMs.isDefined) {
      sb.append("&max_duration_ms=" + maxDurationMs.get)
    }
    if (minDurationMs.isDefined) {
      sb.append("&min_duration_ms=" + minDurationMs.get)
    }
    if (maxEnergy.isDefined) {
      sb.append("&max_energy=" + maxEnergy.get)
    }
    if (minEnergy.isDefined) {
      sb.append("&min_energy=" + minEnergy.get)
    }
    if (maxInstrumentalness.isDefined) {
      sb.append("&max_instrumentalness=" + maxInstrumentalness.get)
    }
    if (minInstrumentalness.isDefined) {
      sb.append("&min_instrumentalness=" + minInstrumentalness.get)
    }
    if (maxKey.isDefined) {
      sb.append("&max_key=" + maxKey.get)
    }
    if (minKey.isDefined) {
      sb.append("&min_key=" + minKey.get)
    }
    if (maxLiveness.isDefined) {
      sb.append("&max_liveness=" + maxLiveness.get)
    }
    if (minLiveness.isDefined) {
      sb.append("&min_liveness=" + minLiveness.get)
    }
    if (maxLoudness.isDefined) {
      sb.append("&max_loudness=" + maxLoudness.get)
    }
    if (minLoudness.isDefined) {
      sb.append("&min_loudness=" + minLoudness.get)
    }
    if (maxMode.isDefined) {
      sb.append("&max_mode=" + maxMode.get)
    }
    if (minMode.isDefined) {
      sb.append("&min_mode=" + minMode.get)
    }
    if (maxPopularity.isDefined) {
      sb.append("&max_popularity=" + maxPopularity.get)
    }
    if (minPopularity.isDefined) {
      sb.append("&min_popularity=" + minPopularity.get)
    }
    if (maxSpeechiness.isDefined) {
      sb.append("&max_speechiness=" + maxSpeechiness.get)
    }
    if (minSpeechiness.isDefined) {
      sb.append("&min_speechiness=" + minSpeechiness.get)
    }
    if (maxTempo.isDefined) {
      sb.append("&max_tempo=" + maxTempo.get)
    }
    if (minTempo.isDefined) {
      sb.append("&min_tempo=" + minTempo.get)
    }
    if (maxTimeSignature.isDefined) {
      sb.append("&max_time_signature=" + maxTimeSignature.get)
    }
    if (minTimeSignature.isDefined) {
      sb.append("&min_time_signature=" + minTimeSignature.get)
    }
    if (maxTimeSignature.isDefined) {
      sb.append("&max_time_signature=" + maxTimeSignature.get)
    }
    if (maxValence.isDefined) {
      sb.append("&max_valence=" + maxValence.get)
    }
    if (minValence.isDefined) {
      sb.append("&min_valence=" + minValence.get)
    }
    if (targetAcousticness.isDefined) {
      sb.append("&target_acousticness=" + targetAcousticness.get)
    }
    if (targetDancability.isDefined) {
      sb.append("&target_dancability=" + targetDancability.get)
    }
    if (targetDurationMs.isDefined) {
      sb.append("&target_duration_ms=" + targetDurationMs.get)
    }
    if (targetEnergy.isDefined) {
      sb.append("&target_energy=" + targetEnergy.get)
    }
    if (targetInstrumentalness.isDefined) {
      sb.append("&target_instrumentalness=" + targetInstrumentalness.get)
    }
    if (targetKey.isDefined) {
      sb.append("&target_key=" + targetKey.get)
    }
    if (targetLiveness.isDefined) {
      sb.append("&target_liveness=" + targetLiveness.get)
    }
    if (targetLoudness.isDefined) {
      sb.append("&target_loudness=" + targetLoudness.get)
    }
    if (targetMode.isDefined) {
      sb.append("&target_mode=" + targetMode.get)
    }
    if (targetPopularity.isDefined) {
      sb.append("&target_popularity=" + targetPopularity.get)
    }
    if (targetSpeechiness.isDefined) {
      sb.append("&target_speechiness=" + targetSpeechiness.get)
    }
    if (targetTempo.isDefined) {
      sb.append("&target_tempo=" + targetTempo.get)
    }
    if (targetTimeSignature.isDefined) {
      sb.append("&target_time_signature=" + targetTimeSignature.get)
    }
    if (targetValence.isDefined) {
      sb.append("&target_valence=" + targetValence.get)
    }

    sb.result()
  }
}
