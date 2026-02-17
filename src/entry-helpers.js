import componentCSSFile from "./component.css";
import cljsFile from "./component.cljs";
import strikethroughCSSFile from "./strikethrough.css";

const TEMPLATE_BLOCK_UID = "roam-render-todo-progress-template";
const TEMPLATE_RENDER_UID = "roam-render-todo-progress-template-render";
const CODE_HEADER_UID = "roam-render-todo-progress-code-parent";
const STRIKETHROUGH_PARENT_UID = "strikethrough-css-parent";
const STRIKETHROUGH_BLOCK_UID = "strikethrough-css";

function toCodeBlock(language, content) {
  return `\`\`\`${language}\n${content}\n\`\`\``;
}

function getBlock(uid, pullPattern = "[:block/uid :block/string]") {
  return roamAlphaAPI.data.pull(pullPattern, [":block/uid", uid]);
}

function blockExists(uid) {
  return Boolean(getBlock(uid, "[:block/uid]"));
}

function removeBlock(uid) {
  if (!blockExists(uid)) return;
  roamAlphaAPI.deleteBlock({ block: { uid } });
}

function uidForToday() {
  return window.roamAlphaAPI.util.dateToPageTitle(new Date());
}

function createPage(title) {
  const pageUID = roamAlphaAPI.util.generateUID();
  roamAlphaAPI.data.page.create({
    page: {
      title,
      uid: pageUID,
    },
  });
  return pageUID;
}

function getPageUidByPageTitle(title) {
  return (
    roamAlphaAPI.q(
      `[:find (pull ?e [:block/uid]) :where [?e :node/title "${title}"]]`
    )?.[0]?.[0]?.uid || null
  );
}

function ensurePage(title) {
  return getPageUidByPageTitle(title) || createPage(title);
}

async function createBlockIfMissing({ parentUID, uid, order, string, open, heading }) {
  if (blockExists(uid)) return false;

  const block = { uid, string };
  if (typeof open === "boolean") block.open = open;
  if (typeof heading === "number") block.heading = heading;

  await roamAlphaAPI.createBlock({
    location: {
      "parent-uid": parentUID,
      order,
    },
    block,
  });

  return true;
}

async function updateBlockStringIfChanged(uid, nextString) {
  const current = getBlock(uid, "[:block/uid :block/string]");
  if (!current || current.string === nextString) return false;

  await roamAlphaAPI.updateBlock({
    block: {
      uid,
      string: nextString,
    },
  });

  return true;
}

async function ensureCodeBlock(parentUID, codeBlockUID) {
  const cljsBlockString = toCodeBlock("clojure", cljsFile);

  if (!blockExists(codeBlockUID)) {
    await createBlockIfMissing({
      parentUID,
      uid: codeBlockUID,
      order: 0,
      string: cljsBlockString,
    });
    return;
  }

  await updateBlockStringIfChanged(codeBlockUID, cljsBlockString);
}

async function ensureCSSBlock(parentUID, cssBlockUID, cssText) {
  const cssBlockString = toCodeBlock("css", cssText);

  if (!blockExists(cssBlockUID)) {
    await createBlockIfMissing({
      parentUID,
      uid: cssBlockUID,
      order: 0,
      string: cssBlockString,
    });
    return;
  }

  await updateBlockStringIfChanged(cssBlockUID, cssBlockString);
}

async function ensureMainRenderBlocks({
  componentName,
  version,
  titleblockUID,
  codeBlockUID,
}) {
  const renderPageUID = ensurePage("roam/render");
  const titleCreated = await createBlockIfMissing({
    parentUID: renderPageUID,
    uid: titleblockUID,
    order: 0,
    string: `${componentName} [[${uidForToday()}]]`,
    open: true,
    heading: 3,
  });

  if (titleCreated) {
    await createBlockIfMissing({
      parentUID: titleblockUID,
      uid: TEMPLATE_BLOCK_UID,
      order: 0,
      string: `${componentName} ${version} [[roam/templates]]`,
      open: true,
    });

    await createBlockIfMissing({
      parentUID: TEMPLATE_BLOCK_UID,
      uid: TEMPLATE_RENDER_UID,
      order: 0,
      string: `{{[[roam/render]]:((${codeBlockUID}))}}`,
    });
  }

  // Always ensure the code header exists (covers fresh install and repair).
  await createBlockIfMissing({
    parentUID: titleblockUID,
    uid: CODE_HEADER_UID,
    order: "last",
    string: "code",
    open: false,
  });

  await ensureCodeBlock(CODE_HEADER_UID, codeBlockUID);
}

async function ensureMainCSSBlocks({ componentName, cssBlockParentUID, cssBlockUID }) {
  const cssPageUID = ensurePage("roam/css");

  await createBlockIfMissing({
    parentUID: cssPageUID,
    uid: cssBlockParentUID,
    order: "last",
    string: `${componentName} STYLE [[${uidForToday()}]]`,
    open: false,
    heading: 3,
  });

  await ensureCSSBlock(cssBlockParentUID, cssBlockUID, componentCSSFile);
}

export async function ensureTodoProgressComponent({
  componentName,
  version,
  titleblockUID,
  codeBlockUID,
  cssBlockParentUID,
  cssBlockUID,
}) {
  await ensureMainRenderBlocks({ componentName, version, titleblockUID, codeBlockUID });
  await ensureMainCSSBlocks({ componentName, cssBlockParentUID, cssBlockUID });
}

export async function toggleStrikethroughCSS(state) {
  if (state === true) {
    const cssPageUID = ensurePage("roam/css");

    await createBlockIfMissing({
      parentUID: cssPageUID,
      uid: STRIKETHROUGH_PARENT_UID,
      order: "last",
      string: `DONE Task Strikethrough STYLE [[${uidForToday()}]]`,
      open: false,
      heading: 3,
    });

    await ensureCSSBlock(STRIKETHROUGH_PARENT_UID, STRIKETHROUGH_BLOCK_UID, strikethroughCSSFile);
    return;
  }

  if (state === false) {
    removeBlock(STRIKETHROUGH_PARENT_UID);
  }
}

