package com.internetter.overheadscalecolor;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OverheadScaleColorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OverheadScaleColorPlugin.class);
		RuneLite.main(args);
	}
}
