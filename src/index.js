import { ensureTodoProgressComponent, toggleStrikethroughCSS } from "./entry-helpers";

const componentName = "TODO Progress Bar";
const version = "v18";

const codeBlockUID = "roam-render-todo-progress-cljs";
const cssBlockUID = "roam-render-todo-progress-css";
const titleblockUID = "roam-render-todo-progress";
const cssBlockParentUID = "todo-progress-css-parent";

async function onload({ extensionAPI }) {
  extensionAPI.settings.panel.create({
    tabTitle: componentName,
    settings: [
      {
        id: "strikethrough",
        name: "Strikethrough DONE tasks",
        description: "Adds CSS to strike through DONE tasks",
        action: {
          type: "switch",
          onChange: async (evt) => {
            await toggleStrikethroughCSS(evt.target.checked);
          },
        },
      },
    ],
  });

  window.todoProgressBarExtensionData = { running: true };

  try {
    await ensureTodoProgressComponent({
      componentName,
      version,
      titleblockUID,
      codeBlockUID,
      cssBlockParentUID,
      cssBlockUID,
    });
  } catch (error) {
    console.error("Error loading plugin:", error);
  }
}

function onunload() {
  window.todoProgressBarExtensionData = null;
}

export default {
  onload,
  onunload,
};
