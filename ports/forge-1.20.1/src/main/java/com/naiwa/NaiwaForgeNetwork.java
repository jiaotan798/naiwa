package com.naiwa;

import com.naiwa.client.ClientBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** 固定两个收发方向，服务端仍校验开镜条件及四格距离。 */
public final class NaiwaForgeNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("naiwa","gameplay"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    static void register() {
        CHANNEL.messageBuilder(NaiwaScopeRequest.class,0,NetworkDirection.PLAY_TO_SERVER)
                .encoder((request,buffer) -> buffer.writeVarInt(request.distance()))
                .decoder(buffer -> new NaiwaScopeRequest(buffer.readVarInt()))
                .consumerMainThread((request,context) -> {
                    var player = context.get().getSender();
                    if (player != null) request.apply(player);
                }).add();
        CHANNEL.messageBuilder(NaiwaFlightHeight.class,1,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((update,buffer) -> buffer.writeDouble(update.height()))
                .decoder(buffer -> new NaiwaFlightHeight(buffer.readDouble()))
                .consumerMainThread((update,context) -> ClientBootstrap.receiveFlight(update.height())).add();
    }
}
