# Group Members: How to add your commands!

I have implemented an automatic command manager that will make better use of commands. This means that the names descriptions and arguments will now be stored in the command file rather than in main.

### There are TWO STEPS

## Step 1: Import the new template

```java
package com.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import java.util.ArrayList;
import java.util.List;

public class YourCommandName implements ICommand {

    @Override
    public String getName() {
        return "your-command"; // Must be lowercase, no spaces
    }

    @Override
    public String getDescription() {
        return "A brief description of what this command does.";
    }

    @Override
    public List<OptionData> getOptions() {
        // If your command needs arguments (like a user to ban, or text to echo), add them here!
        // Otherwise, just return an empty list.
        List<OptionData> options = new ArrayList<>();
        // Example: options.add(new OptionData(OptionType.STRING, "text", "The text to echo", true));
        return options;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Your bot logic goes here!
        event.reply("Hello from my new command!").queue();
    }
}
```

## Step 2: Update MinecraftSocietyBot.java
```
// Command Manager
CommandManager manager = new CommandManager();
// ----------------------------------------------------------------------
// Commands go here:
manager.addCommand(new PingCommand());
manager.addCommand(new YourCommandName()); // <-- Add your command alongside a comment with your name!
// ----------------------------------------------------------------------
```
