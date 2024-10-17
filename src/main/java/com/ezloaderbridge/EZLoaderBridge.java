package com.ezloaderbridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fi.iki.elonen.NanoHTTPD;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.Gson;

public class EZLoaderBridge extends NanoHTTPD implements ModInitializer {
	public static final String MOD_ID = "ezloaderbridge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String API_KEY = "1Qs7QnzHT/9KrVBQWoN2/F/p3g/l97pYuso/ZTDXpzA=";

	private MinecraftServer serverInstance;

	public EZLoaderBridge() {
		super(8080);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("EZLoaderBridge mod initialized!");

		// Capture server instance when server starts
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			this.serverInstance = server;
			LOGGER.info("Server instance acquired for EZLoaderBridge!");
		});

		// Stops HTTP server when the Minecraft server stops
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			stop();
			LOGGER.info("EZLoaderBridge HTTP server stopped. Relaunch the Minecraft server to resume access.");
		});

		//Start http server
		try {
			start(SOCKET_READ_TIMEOUT, false);
			LOGGER.info("EZLoaderBridge HTTP server started on port 8080!");
		} catch (IOException e) {
			LOGGER.error("Couldn't start server!", e);
		}
	}

	@Override
	public Response serve(IHTTPSession session) {
		String uri = session.getUri();
		String apiKey = session.getParameters().getOrDefault("apiKey", Collections.emptyList()).stream().findFirst().orElse(null);

		// Verify API key
		if (!API_KEY.equals(apiKey)) {
            LOGGER.warn("Unauthorized access attempt with invalid API key.");
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized: Invalid API Key");
        }

		// Handle server info request - genereates response in json format
        if ("/server-info".equals(uri)) {
            LOGGER.info("Received request for server info.");
            Response response = newFixedLengthResponse(getServerInfoAsJson());
			response.setMimeType("application/json");
			return response;
        }
		return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
	}

	private String getServerInfoAsJson() {
		Map<String, Object> responseData = new HashMap<>();
		if (serverInstance != null) {
			//Get Minecraft Version
			responseData.put("minecraftVersion", FabricLoader.getInstance().getModContainer("minecraft")
				.map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
				.orElse("Unknown"));
			//Useless get fabric check but it doesnt matter lol
			responseData.put("modloaderType", "Fabric");

			//Mod list: Names, versions. Filter out all the dependency mods and such
			Map<String, String> modsList = new HashMap<>();
			Path modsFolderPath = Paths.get("mods");
			try {
				Files.list(modsFolderPath).filter(Files::isRegularFile).forEach(filePath -> {
					String fileName = filePath.getFileName().toString();
					if (fileName.endsWith(".jar")) {
						// Add jar files from mods folder directly to the mods list without further filtering
						modsList.put(fileName, "1.0.0"); // Default version if not easily accessible from metadata
						LOGGER.info("Added mod from mods folder: " + fileName);
					}
				});
			} catch (IOException e) {
				LOGGER.error("Error accessing mods folder", e);
			}

			responseData.put("mods", modsList);
		} else {
			responseData.put("error", "Server instance not available");
		}

		return new Gson().toJson(responseData);
	}
}