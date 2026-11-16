(() => {
  const form = document.getElementById("scan-form");
  if (!form) return;
  const fileInput = document.getElementById("ledger-image");
  const previewWrap = document.getElementById("image-preview-wrap");
  const preview = document.getElementById("image-preview");
  const filename = document.getElementById("image-filename");
  const filesize = document.getElementById("image-filesize");
  const extractButton = document.getElementById("extract-button");
  const status = document.getElementById("scan-status");
  const reviewPanel = document.getElementById("review-panel");
  const reviewRows = document.getElementById("review-rows");
  const entryCount = document.getElementById("entry-count");
  const saveButton = document.getElementById("save-entries");
  let selectedFile = null;
  let entries = [];
  let objectUrl = null;

  const setStatus = (message, kind = "") => {
    status.textContent = message;
    status.className = "scan-status " + kind;
  };
  const formatBytes = (bytes) => bytes < 1024 * 1024
    ? Math.round(bytes / 1024) + " KB"
    : (bytes / (1024 * 1024)).toFixed(1) + " MB";

  fileInput.addEventListener("change", () => {
    selectedFile = fileInput.files && fileInput.files[0];
    if (!selectedFile) return;
    const allowed = ["image/jpeg", "image/png", "image/webp"];
    if (!allowed.includes(selectedFile.type)) {
      selectedFile = null;
      fileInput.value = "";
      extractButton.disabled = true;
      previewWrap.hidden = true;
      setStatus("Choose a JPG, PNG or WebP image.", "error");
      return;
    }
    if (selectedFile.size > 10 * 1024 * 1024) {
      selectedFile = null;
      fileInput.value = "";
      extractButton.disabled = true;
      previewWrap.hidden = true;
      setStatus("The image is larger than 10 MB.", "error");
      return;
    }
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(selectedFile);
    preview.src = objectUrl;
    filename.textContent = selectedFile.name;
    filesize.textContent = formatBytes(selectedFile.size);
    previewWrap.hidden = false;
    extractButton.disabled = false;
    reviewPanel.hidden = true;
    setStatus("Photo ready. Check that the writing is clear, then continue.");
  });

  document.getElementById("remove-image").addEventListener("click", () => {
    selectedFile = null;
    fileInput.value = "";
    previewWrap.hidden = true;
    extractButton.disabled = true;
    reviewPanel.hidden = true;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    setStatus("");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedFile) return;
    extractButton.disabled = true;
    extractButton.textContent = "Reading handwriting…";
    setStatus("Sending the image securely to the configured AI provider. This can take a little while.");
    const body = new FormData();
    body.append("ledger_image", selectedFile);
    try {
      const response = await fetch("/api/extract", {
        method: "POST",
        headers: {"X-CSRF-Token": document.querySelector('meta[name="csrf-token"]').content},
        body
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || "Could not read this photo.");
      entries = result.entries || [];
      renderEntries();
      reviewPanel.hidden = false;
      if (!entries.length) setStatus("The AI did not find any clear rows. Try a brighter, closer photo.", "error");
      else setStatus("Draft ready. Please check every amount and customer name before saving.", "success");
    } catch (error) {
      setStatus(error.message || "Something went wrong. Please try again.", "error");
    } finally {
      extractButton.disabled = !selectedFile;
      extractButton.innerHTML = 'Read handwriting with AI <span>→</span>';
    }
  });

  function makeInput(value, field, type = "text") {
    const input = document.createElement("input");
    input.type = type;
    input.value = value ?? "";
    input.dataset.field = field;
    if (type === "number") {
      input.min = "0";
      input.step = "0.01";
      input.inputMode = "decimal";
    }
    return input;
  }

  function renderEntries() {
    reviewRows.replaceChildren();
    entryCount.textContent = entries.length + (entries.length === 1 ? " entry" : " entries");
    entries.forEach((entry, index) => {
      const row = document.createElement("tr");
      const values = [
        ["customer_name", "text"],
        ["date", "date"],
        ["description", "text"],
        ["type", "select"],
        ["amount", "number"]
      ];
      values.forEach(([field, type]) => {
        const cell = document.createElement("td");
        if (type === "select") {
          const select = document.createElement("select");
          select.dataset.field = field;
          [["credit", "Credit"], ["payment", "Payment"]].forEach(([value, label]) => {
            const option = document.createElement("option");
            option.value = value;
            option.textContent = label;
            select.append(option);
          });
          select.value = entry[field] === "payment" ? "payment" : "credit";
          cell.append(select);
        } else {
          cell.append(makeInput(entry[field], field, type));
        }
        row.append(cell);
      });
      const removeCell = document.createElement("td");
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "delete-button";
      remove.textContent = "×";
      remove.setAttribute("aria-label", "Remove row");
      remove.addEventListener("click", () => {
        entries.splice(index, 1);
        renderEntries();
      });
      removeCell.append(remove);
      row.append(removeCell);
      reviewRows.append(row);
    });
  }

  document.getElementById("cancel-review").addEventListener("click", () => {
    entries = [];
    reviewRows.replaceChildren();
    reviewPanel.hidden = true;
    setStatus("Draft discarded. Your ledger has not been changed.");
  });

  saveButton.addEventListener("click", async () => {
    const rows = Array.from(reviewRows.querySelectorAll("tr"));
    const confirmed = rows.map((row) => {
      const item = {};
      row.querySelectorAll("[data-field]").forEach((input) => item[input.dataset.field] = input.value);
      item.amount = Number(item.amount);
      return item;
    }).filter((item) => item.customer_name.trim() && Number.isFinite(item.amount) && item.amount > 0);
    if (!confirmed.length) {
      setStatus("Add a customer name and a valid amount to at least one row.", "error");
      return;
    }
    saveButton.disabled = true;
    saveButton.textContent = "Saving confirmed entries…";
    try {
      const response = await fetch("/api/transactions/bulk", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": document.querySelector('meta[name="csrf-token"]').content
        },
        body: JSON.stringify({entries: confirmed})
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || "Could not save entries.");
      setStatus(result.message + " Opening your customer list…", "success");
      reviewPanel.hidden = true;
      entries = [];
      window.setTimeout(() => { window.location.href = "/customers"; }, 900);
    } catch (error) {
      setStatus(error.message || "Could not save entries.", "error");
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = "Confirm & save entries →";
    }
  });
})();