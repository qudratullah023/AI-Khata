(() => {
  const root = document.documentElement;
  const storedTheme = localStorage.getItem("ai-khata-theme");
  const prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  const applyTheme = (theme) => {
    root.dataset.theme = theme;
    localStorage.setItem("ai-khata-theme", theme);
    document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
      const symbol = button.querySelector(".theme-symbol");
      const label = button.querySelector(".theme-label");
      if (symbol) symbol.textContent = theme === "dark" ? "☀" : "☾";
      if (label) label.textContent = theme === "dark" ? "Light appearance" : "Dark appearance";
      button.setAttribute("aria-label", theme === "dark" ? "Switch to light theme" : "Switch to dark theme");
    });
  };
  applyTheme(storedTheme || (prefersDark ? "dark" : "light"));
  document.addEventListener("click", (event) => {
    const toggle = event.target.closest("[data-theme-toggle]");
    if (toggle) applyTheme(root.dataset.theme === "dark" ? "light" : "dark");
    const close = event.target.closest(".flash-close");
    if (close) close.closest(".flash")?.remove();
  });
})();