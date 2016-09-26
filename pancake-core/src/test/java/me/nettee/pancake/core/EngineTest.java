package me.nettee.pancake.core;

import org.junit.Test;

import me.nettee.pancake.core.Engine;

public class EngineTest {
	
	@Test
	public void testExecute() {
		String sql = "select * from user, address";
		Engine engine = new Engine();
		engine.execute(sql);
	}
	
}
