package me.nettee.pancake.cli;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;

public class Main {
	
	private static final String PROMPT = "> ";
	
	public static void main(String[] args) {
		welcome();
		loop();
	}
	
	private static void welcome() {
		
		try {
			InputStream is = Main.class.getResourceAsStream("/project.properties");
			Properties properties = new Properties();
			properties.load(is);
			
			String description = properties.getProperty("project.description");
			String version = properties.getProperty("project.version");
			
			System.out.println(description + " " + version);
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void goodbye() {
		System.out.println();
		System.out.println("Bye.");
	}
	
	private static void loop() {
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			goodbye();
		}));
		
		Scanner scanner = new Scanner(System.in);
		while (true) {
			try {
			System.out.print(PROMPT);
			String cmd = scanner.nextLine();
			command(cmd);
			} catch (NoSuchElementException e) {
				break;
			}
		}
	}
	
	private static void command(String cmd) {
		
	}
}
