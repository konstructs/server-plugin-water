package org.konstructs.grass;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.typesafe.config.ConfigValue;
import konstructs.api.*;
import konstructs.api.messages.BlockUpdateEvent;
import konstructs.api.messages.BoxQueryResult;
import konstructs.api.messages.GlobalConfig;
import konstructs.plugin.Config;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class WaterActor extends KonstructsActor {

    public WaterActor(ActorRef universe) {
        super(universe);
    }

    @Override
    public void onReceive(Object message) {
        super.onReceive(message);
    }

    @PluginConstructor
    public static Props props(String pluginName, ActorRef universe) {
        return Props.create(WaterActor.class, universe);
    }
}
