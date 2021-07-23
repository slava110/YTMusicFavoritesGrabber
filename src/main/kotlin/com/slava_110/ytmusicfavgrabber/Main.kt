package com.slava_110.ytmusicfavgrabber

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.StoredCredential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Video
import com.google.common.collect.Lists
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

private val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()
private val JSON_FACTORY: JsonFactory = GsonFactory()

fun main() {
    println("Authorization")

    val credential = authorize()

    val youtube = YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName("YTMusicGrabber")
        .build()

    val videos = mutableListOf<Video>()
    var maxChannelNameLength = 0 // Format output
    var maxTitleLength = 0       // Format output
    var nextPageToken:String? = null

    println("Starting video grab process...")

    do {
        val res = youtube.videos().list("snippet,id")
            .setMyRating("like")
            .setMaxResults(50)
            .setPageToken(nextPageToken)
            .execute()

        res.items.stream()
            .filter { it.snippet.categoryId.equals("10") }
            .forEach {
                if(it.snippet.channelTitle.length > maxChannelNameLength)
                    maxChannelNameLength = it.snippet.channelTitle.length
                if(it.snippet.title.length > maxTitleLength)
                    maxTitleLength = it.snippet.title.length

                videos.add(it)
            }
        println("> Found ${res.items.size} music videos. Next page token: ${res.nextPageToken}")

        nextPageToken = res.nextPageToken
    } while (nextPageToken != null)

    println("Found ${videos.size} music videos in total")

    Files.write(Paths.get("ytmusicsongs.txt"), videos.stream()
        .map {
            "${it.snippet.channelTitle.padEnd(maxChannelNameLength)} | ${it.snippet.title.padEnd(maxTitleLength)} | https://music.youtube.com/watch?v=${it.id}"
        }
        .collect(Collectors.toList())
    )
}

private fun authorize(): Credential {
    val scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube")

    val secrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(object {}.javaClass.getResourceAsStream("/client_secret.json")!!))

    val fileDataStoreFactory = FileDataStoreFactory(File("."))
    val datastore = fileDataStoreFactory.getDataStore<StoredCredential>("yt_music_grabber_stored_token")

    val flow = GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, secrets, scopes)
        .setCredentialDataStore(datastore)
        .build()

    val localReceiver = LocalServerReceiver.Builder().setPort(8080).build()

    return AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user")
}