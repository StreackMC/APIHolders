package com.github.streackmc.APIHolders;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fi.iki.elonen.NanoHTTPD;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

public class plugin extends JavaPlugin {

  public HTTPServer httpServer;
  public FileConfiguration conf;
  public StreackLib StreackLib;
  public String path;
  public Boolean whiteMode;
  public List<?> rawList;
  // public LiteralArgumentBuilder<CommandSourceStack> commandTree = Commands.literal("api-holders");

  @Override
  public void onEnable() {
    getLogger().info("正在启用APIHolders...");
    /* 检测 StreackLib */
    if (!Bukkit.getPluginManager().isPluginEnabled("StreackLib")) {
      getLogger().severe("启用失败：未检测到StreackLib");
      getServer().getPluginManager().disablePlugin(this);
      return;
    } else {
      StreackLib = new StreackLib();
    }
    /* 检测 PlaceholderAPI */
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      getLogger().severe("启用失败：未检测到PlaceholderAPI");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    /* 读入 StreackLib:HTTPServer */
    httpServer = StreackLib.getHttpServer();
    if (httpServer == null) {
      getLogger().warning("启用失败：StreackLib的HTTPServer模块无法启用或无法与之通信。");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    /* 读取配置 */
    reloadConf();
    /* 开启HTTPServer */
    registerHttpHandler();
  }
  @Override
  public void onDisable() {
    getLogger().info("正在禁用APIHolders...");
  }

  /**
   * 载入配置并格式化
   * @return void
   */
  private void reloadConf() {
    // 当前版本禁止再次调用，因为StreackLib还不支持取消注册监听
    // 目前实现，未来使用
    saveDefaultConfig();
    conf = getConfig();
    path = conf.getString("path", "/api/placeholder");
    whiteMode = conf.getBoolean("white-mode", true);
    rawList = conf.getList("list");
  }

  // /**
  //  * 注册命令监听
  //  * @return void
  //  */
  // private void registerCommand() {
  //   commandTree.then()
  // }

  /**
   * 注册HTTPServer事件监听
   * @return void
   */
  private void registerHttpHandler() {
    httpServer.registerHandler(path, session -> {
      try {
        /* 仅处理 GET */
        if (!NanoHTTPD.Method.GET.equals(session.getMethod())) {
          return newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
              NanoHTTPD.MIME_PLAINTEXT, "Not allowed ");
        }
        /* 读取参数 */
        String query = session.getParameters["query"];
        String target = session.getParms().get("target");
        if (query == null || query.isEmpty()) {
          return jsonResponse(400, "Missing parameter: query", null, null);
        }

        /* 名单过滤 */
        if (!passFilter(query)) {
          return jsonResponse(403, "Placeholder forbidden by list", null, null);
        }

        /* 解析目标 */
        String parsed;
        if (target == null || target.equalsIgnoreCase("server") || target.equalsIgnoreCase("console")) {
          parsed = PlaceholderAPI.setPlaceholders(null, "%" + query + "%");
        } else {
          parsed = PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(target), "%" + query + "%");
        }

        /* 返回 JSON */
        return jsonResponse(200, "OK", parsed);
      } catch (Exception ex) {
        ex.printStackTrace();
        return jsonResponse(500, "Internal server error", null, null);
      }
    });
    getLogger().info("已注册 HTTP 监听路径: " + path);
  }

  /**
   * 判定输入的Placeholder是否允许使用
   * @param placeholder 要判断的PlaceholderAPI
   * @return boolean
   */
  private boolean passFilter(String placeholder) {
    if (rawList == null || rawList.isEmpty()) {
      /* 空名单：白名单默认拒绝，黑名单默认通过 */
      return !whiteMode;
    }
    boolean matchAny = rawList.stream().anyMatch(obj -> {
      if (obj instanceof String) {
        String str = (String) obj;
        /* 正则 */
        if (str.startsWith("regex:")) {
          try {
            return Pattern.compile(str.substring(6)).matcher(placeholder).find();
          } catch (PatternSyntaxException ignore) {
            return false;
          }
        }
        /* 普通字符串 */
        return str.equalsIgnoreCase(placeholder);
      }
      return false;
    });
    return whiteMode ? matchAny : !matchAny;
  }

  /**
   * 快速封装 JSON 响应
   * @param int code: HTTP状态码，默认500
   * @param String info: 对状态码的解释，默认空
   * @param String mc: 以MC格式返回的结果，默认空
   * @return 封装完毕的JSON对象
   */
  @SuppressWarnings("unchecked")
  private NanoHTTPD.Response jsonResponse(Integer code, String info, String mc) {
    if (code == null) { code = 500; }
    if (mc == null) { mc = ""; }
    if (info == null) { info = ""; }
    JSONObject status = new JSONObject();
    status.put("code", code);
    status.put("info", info);
    JSONObject respond = new JSONObject();
    respond.put("mc", mc);
    respond.put("plain", mc.replaceAll("§[0-9a-zA-Z]", ""));
    JSONObject root = new JSONObject();
    root.put("status", status);
    root.put("result", respond);
    return newFixedLengthResponse(NanoHTTPD.Response.Status.lookup(code),
        "application/json", root.toJSONString());
  }
}