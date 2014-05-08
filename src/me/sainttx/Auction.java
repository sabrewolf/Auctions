package me.sainttx;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

import mkremins.fanciful.FancyMessage;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Auction extends JavaPlugin {
	private ArrayList<String> ignoring = new  ArrayList<String>();
	private YamlConfiguration messages;
	private YamlConfiguration names;
	//private YamlConfiguration log;
	private IAuction auction;

	public static Economy economy = null;

	//	private boolean logauctions;
	//	private boolean allowautowin;
	//	private boolean allowcancel;
	//	private boolean allowcreative;
	//	private int timebetween;
	//	private int bidincrease;
	//	private int auctiontime;
	//	private int cost;
	//	private int percent;

	/*
	 * commands:
	 * auction info - show info about current auction
	 * auction start - start an auction
	 * auction end - end an auction
	 * auction quiet - silence auction messages
	 */

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		loadConfig();
		setupEconomy();
		getCommand("auction").setExecutor(this);
	}

	private boolean setupEconomy() {
		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (economyProvider != null) {
			economy = economyProvider.getProvider();
		}
		return (economy != null);
	}

	public static Economy getEconomy() {
		return economy;
	}

	private void loadConfig() {
		//		logauctions = getConfig().getBoolean("log-auctions");
		//		allowautowin = getConfig().getBoolean("allow-autowin");
		//		allowcancel = getConfig().getBoolean("allow-cancel");
		//		allowcreative = getConfig().getBoolean("allow-creative");
		//		timebetween = getConfig().getInt("time-between-bidding");
		//		bidincrease = getConfig().getInt("minimum-bid-increment");
		//		auctiontime = getConfig().getInt("auction-time");
		//		cost = getConfig().getInt("auction-start-fee");
		//		percent = getConfig().getInt("auction-tax-percentage");
		File messages = new File(getDataFolder(), "messages.yml");
		File names = new File(getDataFolder(), "items.yml");
		if (!messages.exists()) {
			saveResource("messages.yml", false);
		}
		if (!names.exists()) {
			saveResource("items.yml", false);
		}
		this.messages = YamlConfiguration.loadConfiguration(messages);
		this.names = YamlConfiguration.loadConfiguration(names);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String username = sender.getName();
		if (args.length == 0) {
			sendMenu(sender);
		} else {
			String arg1 = args[0];
			if (arg1.equals("start")) {
				if (!ignoring.contains(username)) {
					startAuction(sender, args);
				} else {
					sender.sendMessage(getMessageFormatted("fail-start-ignoring"));
				}
			} else if (arg1.equals("end")) {
				if (this.auction != null) {
					auction.end();
					stopAuction();
				} else {
					sender.sendMessage(getMessageFormatted("fail-end-no-auction"));
				}
			} else if (arg1.equals("info")) {
				if (auction != null) {
					sender.sendMessage(getMessageFormatted("auction-info-message"));
				} else {
					sender.sendMessage(getMessageFormatted("fail-info-no-auction"));
				}
			} else if (arg1.equals("bid")) {
				if (auction != null) {
					if (args.length == 2) {
						auction.bid((Player) sender, Integer.parseInt(args[1])); // could throw
					} else {
						sender.sendMessage(getMessageFormatted("fail-bid-syntax"));
					}
				} else {
					sender.sendMessage(getMessageFormatted("fail-bid-no-auction"));
				}
			} else if (arg1.equals("quiet") || arg1.equals("ignore")) {
				if (!ignoring.contains(sender.getName())) {
					sender.sendMessage(getMessageFormatted("ignoring-on"));
					ignoring.add(sender.getName());
				} else {
					sender.sendMessage(getMessageFormatted("ignoring-off"));
					ignoring.remove(sender.getName());
				}
			} else if (arg1.equals("reload")) {
				if (sender.hasPermission("auction.reload")) {
					reloadConfig();
					loadConfig();	
				} else {
					sender.sendMessage(getMessageFormatted("insufficient-permissions"));
				}
			} else {
				sendMenu(sender); // invalid arg
			}
		}
		return false;
	}

	private String itemName(ItemStack item) {
		short durability = item.getType().getMaxDurability() > 0 ? 0 : item.getDurability();
		String search = item.getType().toString() + "." + durability;
		String ret = names.getString(search);
		if (ret == null) {
			ret = "null";
		}
		return ret;
	}

	public void messageListening(String message) {
		FancyMessage message0 = new FancyMessage("");
		String message1 = replace(message);

		String[] split = message1.split("%i");
		if (message1.contains("%i")) {
			for (int i = 0 ; i < split.length ; i++) {
				if (i != split.length -1) {
					message0.then(format(split[i]))
					.then(itemName(auction.getItem()))
					.itemTooltip(auction.getItem());
				} else {
					if (message1.endsWith("%i")) {
						if (split.length == 1) message0.then(format(split[i]));
						message0.then(itemName(auction.getItem()))
						.itemTooltip(auction.getItem());
					} else {
						message0.then(format(split[i]));
					}
				}
				message0.color(getIColor("color"));
				if (!messages.getString("%i.style").equals("none")) {
					message0.style(getIColor("style"));
				}
			}
		} else {
			message0.then(format(message1));
		}

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!ignoring.contains(player.getName())) {
				message0.send(player);
			}
		}
	}

	private ChatColor getIColor(String type) {
		return ChatColor.getByChar(messages.getString("%i." + type));		
	}

	public String replace(String message) {
		String ret = messages.getString(message);
		ret = ret.replaceAll("%t", auction.getFormattedTime())
				.replaceAll("%b", Integer.toString(auction.getCurrentBid()))
				.replaceAll("%p", UUIDtoName(auction.getOwner()))
				.replaceAll("%a", Integer.toString(auction.getNumItems()));
		if (auction.hasBids()) {
			ret = ret.replaceAll("%T", Integer.toString(auction.getCurrentTax()))
					.replaceAll("%w", UUIDtoName(auction.getWinning()));
		}
		return ret;
	}

	public String UUIDtoName(UUID uuid) {
		return Bukkit.getOfflinePlayer(uuid).getName();
	}

	public YamlConfiguration getMessages() {
		return this.messages;
	}

	private void sendMenu(CommandSender sender) {
		for (Iterator<String> info = messages.getStringList("auction-menu").iterator(); info.hasNext();) {
			sender.sendMessage(format(info.next()));
		}
	}

	private void startAuction(CommandSender sender, String[] args) {
		if (this.auction != null) {
			sender.sendMessage(getMessageFormatted("fail-start-auction-in-progress"));
			return;
		}
		if (sender instanceof Player) {
			if (args.length > 2) {
				try {
					int amount = Integer.parseInt(args[1]);
					int start = Integer.parseInt(args[2]);
					int autowin = -1;
					if (args.length == 4) { // auction start amount startingbid autowin
						autowin = Integer.parseInt(args[3]);
					}
					this.auction = new IAuction(this, (Player) sender, amount, start, autowin);
					this.auction.start();
				} catch (NumberFormatException ex1) {
					sender.sendMessage(getMessageFormatted("fail-number-format"));
				} catch (InsufficientItemsException ex2) {
					sender.sendMessage(getMessageFormatted("fail-start-not-enough-items"));	
				} catch (EmptyHandException ex3) {
					sender.sendMessage(getMessageFormatted("fail-start-handempty"));	
				}
			} else {
				sender.sendMessage(getMessageFormatted("fail-start-syntax"));
			}
		} else {
			sender.sendMessage(getMessageFormatted("fail-console"));
		}
	}

	public void stopAuction() {
		this.auction = null;
	}

	public String getMessageFormatted(String message) {
		return format(messages.getString(message));
	}

	private String format(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}
}