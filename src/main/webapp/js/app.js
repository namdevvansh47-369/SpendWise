const API_URL = "api/transactions";
const BUDGET_API_URL = "api/budget";

const transactionList = document.getElementById("transactionList");
const transactionModal = document.getElementById("transactionModal");
const transactionForm = document.getElementById("transactionForm");
const formMessage = document.getElementById("formMessage");
const saveButton = document.getElementById("saveButton");
const searchInput = document.getElementById("searchInput");

let transactions = [];
let editingId = null;
let spendingChart = null;

const money = amount =>
    new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR"
    }).format(Number(amount) || 0);

function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}

function formatDate(dateString) {
    if (!dateString) return "—";

    const parts = dateString.slice(0, 10).split("-");
    if (parts.length !== 3) return escapeHTML(dateString);

    return `${parts[2]}-${parts[1]}-${parts[0]}`;
}

function renderTransactions() {
    const query = searchInput.value.trim().toLowerCase();

    const filtered = transactions.filter(t =>
        [t.description, t.category, t.type, t.transaction_date]
            .some(value => String(value ?? "").toLowerCase().includes(query))
    );

    if (filtered.length === 0) {
        transactionList.innerHTML = `
            <tr>
                <td colspan="5" style="text-align:center;padding:24px">
                    ${transactions.length ? "No matching transactions." : "No transactions yet. Add your first one!"}
                </td>
            </tr>`;
        return;
    }

transactionList.innerHTML = filtered.map(t => `
    <tr>
        <td>
            <strong>${escapeHTML(t.description)}</strong>
            <small style="display:block">${escapeHTML(t.type)}</small>
        </td>
        <td>${escapeHTML(t.category)}</td>
        <td>${escapeHTML(formatDate(t.transaction_date))}</td>
        <td style="color:${t.type === "INCOME" ? "#16803c" : "#c0392b"}">
            ${t.type === "INCOME" ? "+" : "−"}${money(t.amount)}
        </td>
        <td>
            <button type="button" class="edit-btn"
                    data-id="${t.id}">Edit</button>
            <button type="button" class="delete-btn"
                    data-id="${t.id}">Delete</button>
        </td>
    </tr>
`).join("");


}

function updateSummary() {
    const now = new Date();
    const currentMonth = now.getMonth();
    const currentYear = now.getFullYear();

    const monthlyTransactions = transactions.filter(t => {
        const parts = String(t.transaction_date).slice(0, 10).split("-");
        if (parts.length !== 3) return false;

        return Number(parts[1]) - 1 === currentMonth &&
               Number(parts[0]) === currentYear;
    });

    const income = monthlyTransactions
        .filter(t => t.type === "INCOME")
        .reduce((sum, t) => sum + Number(t.amount), 0);

    const expenses = monthlyTransactions
        .filter(t => t.type === "EXPENSE")
        .reduce((sum, t) => sum + Number(t.amount), 0);

    // Balance is based on all transactions returned by the API.
    const totalIncome = transactions
        .filter(t => t.type === "INCOME")
        .reduce((sum, t) => sum + Number(t.amount), 0);

    const totalExpenses = transactions
        .filter(t => t.type === "EXPENSE")
        .reduce((sum, t) => sum + Number(t.amount), 0);

    document.getElementById("balance").textContent =
        money(totalIncome - totalExpenses);
    document.getElementById("income").textContent = money(income);
    document.getElementById("expenses").textContent = money(expenses);

    const savingsRate = income > 0
        ? ((income - expenses) / income) * 100
        : 0;

    document.getElementById("savings").textContent =
        `${savingsRate.toFixed(1)}%`;
}

function updateSpendingChart() {
    const canvas = document.getElementById("spendingChart");
    const message = document.getElementById("chartMessage");

    if (!canvas || typeof Chart === "undefined") {
        if (message) {
            message.textContent = "Chart could not load. Check your internet connection.";
        }
        return;
    }

    // Only expenses are included in the chart.
    const categoryTotals = {};

    transactions
        .filter(t => t.type === "EXPENSE")
        .forEach(t => {
            const category = t.category || "Other";
            categoryTotals[category] =
                (categoryTotals[category] || 0) + Number(t.amount);
        });

    const labels = Object.keys(categoryTotals);
    const values = Object.values(categoryTotals);

    if (spendingChart) {
        spendingChart.destroy();
        spendingChart = null;
    }

    if (labels.length === 0) {
        message.textContent = "No expense transactions available yet.";
        return;
    }

    message.textContent = "Your expenses by category";

    spendingChart = new Chart(canvas, {
        type: "doughnut",
        data: {
            labels: labels,
            datasets: [{
                data: values,
                backgroundColor: [
                    "#10b981",
                    "#3b82f6",
                    "#f59e0b",
                    "#8b5cf6",
                    "#ef4444",
                    "#06b6d4",
                    "#ec4899",
                    "#84cc16"
                ],
                borderWidth: 2,
                borderColor: "#ffffff",
                hoverOffset: 8
            }]
        },

options: {
    responsive: true,
    maintainAspectRatio: false,
    cutout: "72%",
    layout: {
        padding: 8
    },
    animation: {
        animateRotate: true,
        duration: 700
    },
    plugins: {
        legend: {
            position: "bottom",
            labels: {
                usePointStyle: true,
                pointStyle: "circle",
                padding: 18,
                boxWidth: 8,
                color: "#64748b",
                font: {
                    size: 11,
                    family: "'Segoe UI', Arial, sans-serif"
                }
            }
        },
        tooltip: {
            backgroundColor: "#111c35",
            titleColor: "#ffffff",
            bodyColor: "#e5edff",
            padding: 12,
            cornerRadius: 10,
            callbacks: {
                label: function(context) {
                    return context.label + ": " +
                        money(context.raw);
                }
            }
        }
    }
}

    });
}

let monthlyTrendChart = null;

function updateMonthlyTrendChart() {
    const canvas = document.getElementById("monthlyTrendChart");
    const message = document.getElementById("monthlyTrendMessage");

    if (!canvas || typeof Chart === "undefined") {
        if (message) {
            message.textContent = "Monthly chart could not load.";
        }
        return;
    }

    const now = new Date();
    const months = [];

    // Current month and previous 5 months
    for (let i = 5; i >= 0; i--) {
        const date = new Date(
            now.getFullYear(),
            now.getMonth() - i,
            1
        );

        const key =
            `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;

        months.push({
            key,
            label: date.toLocaleDateString("en-IN", {
                month: "short",
                year: "2-digit"
            }),
            income: 0,
            expenses: 0
        });
    }

    const monthMap = new Map(months.map(month => [month.key, month]));

    transactions.forEach(transaction => {
        const date = String(transaction.transaction_date ?? "").slice(0, 10);
        const monthKey = date.slice(0, 7);
        const month = monthMap.get(monthKey);

        if (!month) return;

        const amount = Number(transaction.amount) || 0;

        if (transaction.type === "INCOME") {
            month.income += amount;
        } else if (transaction.type === "EXPENSE") {
            month.expenses += amount;
        }
    });

    if (monthlyTrendChart) {
        monthlyTrendChart.destroy();
    }

    monthlyTrendChart = new Chart(canvas, {
        type: "bar",
        data: {
            labels: months.map(month => month.label),
            datasets: [
                {
                    label: "Income",
                    data: months.map(month => month.income),
                    backgroundColor: "#356df3",
                    borderRadius: 6
                },
                {
                    label: "Expenses",
                    data: months.map(month => month.expenses),
                    backgroundColor: "#8ba9f8",
                    borderRadius: 6
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: "index",
                intersect: false
            },
            plugins: {
                legend: {
                    position: "bottom",
                    labels: {
                        usePointStyle: true,
                        padding: 18
                    }
                },
                tooltip: {
                    callbacks: {
                        label: context =>
                            `${context.dataset.label}: ${money(context.raw)}`
                    }
                }
            },
            scales: {
                x: {
                    grid: {
                        display: false
                    }
                },
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: value => money(value)
                    }
                }
            }
        }
    });

    message.textContent =
        "Monthly totals are calculated from your transactions.";
}

function updateMonthlyInsights() {
    const comparisonElement = document.getElementById("expenseComparison");
    const comparisonDetail = document.getElementById("expenseComparisonDetail");
    const topCategoryElement = document.getElementById("topSpendingCategory");
    const topCategoryDetail = document.getElementById("topSpendingDetail");
    const spentPercentageElement = document.getElementById("incomeSpentPercentage");
    const spentDetail = document.getElementById("incomeSpentDetail");

    if (!comparisonElement || !topCategoryElement || !spentPercentageElement) {
        return;
    }

    const now = new Date();

    const currentMonthKey =
        `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;

    const previousDate = new Date(now.getFullYear(), now.getMonth() - 1, 1);

    const previousMonthKey =
        `${previousDate.getFullYear()}-${String(previousDate.getMonth() + 1).padStart(2, "0")}`;

    const currentMonthTransactions = transactions.filter(t =>
        String(t.transaction_date ?? "").slice(0, 7) === currentMonthKey
    );

    const previousMonthTransactions = transactions.filter(t =>
        String(t.transaction_date ?? "").slice(0, 7) === previousMonthKey
    );

    const sumType = (items, type) =>
        items
            .filter(t => t.type === type)
            .reduce((sum, t) => sum + (Number(t.amount) || 0), 0);

    const currentExpenses = sumType(currentMonthTransactions, "EXPENSE");
    const previousExpenses = sumType(previousMonthTransactions, "EXPENSE");
    const currentIncome = sumType(currentMonthTransactions, "INCOME");

    // Compare this month's expenses with last month's.
    if (previousExpenses === 0) {
        comparisonElement.textContent =
            currentExpenses === 0 ? "No expenses" : "No prior baseline";

        comparisonDetail.textContent =
            currentExpenses === 0
                ? "No expenses recorded this month."
                : "Last month had no recorded expenses to compare.";
    } else {
        const change =
            ((currentExpenses - previousExpenses) / previousExpenses) * 100;

        comparisonElement.textContent =
            `${change > 0 ? "+" : ""}${change.toFixed(1)}%`;

        comparisonElement.style.color =
            change > 0 ? "#dc2626" :
            change < 0 ? "#16803c" : "#64748b";

        comparisonDetail.textContent =
            `${change > 0 ? "More" : change < 0 ? "Less" : "Same"} spending than last month`;
    }

    // Find the category with the highest expenses this month.
    const categoryTotals = {};

    currentMonthTransactions
        .filter(t => t.type === "EXPENSE")
        .forEach(t => {
            const category = t.category || "Other";
            categoryTotals[category] =
                (categoryTotals[category] || 0) + (Number(t.amount) || 0);
        });

    const topCategory = Object.entries(categoryTotals)
        .sort((a, b) => b[1] - a[1])[0];

    if (topCategory) {
        topCategoryElement.textContent = topCategory[0];
        topCategoryDetail.textContent =
            `${money(topCategory[1])} spent this month`;
    } else {
        topCategoryElement.textContent = "No spending yet";
        topCategoryDetail.textContent =
            "Your category insights will appear when you add expenses.";
    }

    // Calculate the percentage of this month's income spent.
    if (currentIncome > 0) {
        const percentage = (currentExpenses / currentIncome) * 100;

        spentPercentageElement.textContent = `${percentage.toFixed(1)}%`;
        spentPercentageElement.style.color =
            percentage > 100 ? "#dc2626" : "#356df3";

        spentDetail.textContent =
            `${money(currentExpenses)} spent from ${money(currentIncome)} income`;
    } else {
        spentPercentageElement.textContent = "—";
        spentDetail.textContent =
            "Add income to calculate your spending percentage.";
    }
}
loadTransactions

const budgetMonthInput = document.getElementById("budgetMonth");
const budgetAmountInput = document.getElementById("budgetAmount");
const budgetForm = document.getElementById("budgetForm");
const budgetMessage = document.getElementById("budgetMessage");
const budgetSaveButton = document.getElementById("budgetSaveButton");

function getCurrentMonth() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function setBudgetMessage(message, isError = false) {
    budgetMessage.textContent = message;
    budgetMessage.style.color = isError ? "#c0392b" : "#16803c";
}


async function loadBudget() {
    const month = budgetMonthInput.value;

    if (!month) return;

    document.getElementById("budgetStatus").textContent =
        "Loading budget...";

    try {
        const response = await fetch(
            `${BUDGET_API_URL}?month=${encodeURIComponent(month)}`,
            {
                credentials: "same-origin",
                cache: "no-store"
            }
        );

        if (response.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!response.ok) {
            throw new Error(`Request failed: ${response.status}`);
        }

        const data = await response.json();

        const budget = Number(data.budget) || 0;
        const expenses = Number(data.expenses) || 0;
        const remaining = budget - expenses;

        document.getElementById("budgetSpent").textContent =
            money(expenses);

        document.getElementById("budgetLimit").textContent =
            money(budget);

        const percentage = budget > 0
            ? (expenses / budget) * 100
            : 0;

        const progress = document.getElementById("budgetProgress");

        progress.style.width = `${Math.min(percentage, 100)}%`;

        progress.style.backgroundColor =
            percentage >= 100 ? "#c0392b" :
            percentage >= 80 ? "#f59e0b" : "#10b981";

        const status = document.getElementById("budgetStatus");

        if (budget <= 0) {
            status.textContent =
                "No budget set for this month. Enter an amount above.";
        } else if (remaining < 0) {
            status.textContent =
                `Over budget by ${money(Math.abs(remaining))} (${percentage.toFixed(1)}% used).`;
        } else if (percentage >= 80) {
            status.textContent =
                `${percentage.toFixed(1)}% used. ${money(remaining)} remaining — spending is close to your limit.`;
        } else {
            status.textContent =
                `${percentage.toFixed(1)}% used. ${money(remaining)} remaining.`;
        }

        budgetAmountInput.value = budget > 0 ? budget : "";
        setBudgetMessage("");

    } catch (error) {
        console.error("Could not load budget:", error);

        document.getElementById("budgetStatus").textContent =
            "Unable to load this month's budget. Check your connection and try again.";

        setBudgetMessage(
            "Budget could not be loaded. Your saved budget may still exist.",
            true
        );
    }
}


budgetForm.addEventListener("submit", async event => {
    event.preventDefault();

    const month = budgetMonthInput.value;
    const budget = Number(budgetAmountInput.value);

    if (!month || !Number.isFinite(budget) || budget <= 0) {
        setBudgetMessage("Enter a valid month and positive budget.", true);
        return;
    }

    budgetSaveButton.disabled = true;
    budgetSaveButton.textContent = "Saving...";
    setBudgetMessage("");

    try {
        const response = await fetch(BUDGET_API_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ month, budget })
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText || `Request failed: ${response.status}`);
        }

        setBudgetMessage("Monthly budget saved successfully!");
        await loadBudget();

    } catch (error) {
        console.error("Could not save budget:", error);
        setBudgetMessage("Could not save budget. Please try again.", true);
    } finally {
        budgetSaveButton.disabled = false;
        budgetSaveButton.textContent = "Save budget";
    }
});

budgetMonthInput.addEventListener("change", loadBudget);

budgetMonthInput.value = getCurrentMonth();



async function loadTransactions() {
    transactionList.innerHTML = `
        <tr>
            <td colspan="5">
                <div class="sw-status sw-status-loading">
                    <span class="sw-spinner"></span>
                    Loading your transactions...
                </div>
            </td>
        </tr>`;

    try {
        const response = await fetch(API_URL, {
            credentials: "same-origin",
            cache: "no-store"
        });

        if (response.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const data = await response.json();

        if (!Array.isArray(data)) {
            throw new Error("Unexpected transactions response.");
        }

        transactions = data;

        renderTransactions();
        updateSummary();
        updateSpendingChart();
        updateMonthlyTrendChart();
        updateMonthlyInsights();

    } catch (error) {
        console.error("Could not load transactions:", error);

        transactionList.innerHTML = `
            <tr>
                <td colspan="5">
                    <div class="sw-status sw-status-error">
                        <strong>Unable to load transactions</strong>
                        <p>
                            Check your connection and make sure the
                            server is running, then try again.
                        </p>
                        <button
                            type="button"
                            class="retry-transactions-btn">
                            Try again
                        </button>
                    </div>
                </td>
            </tr>`;
    }
}


document.getElementById("addButton").addEventListener("click", () => {
    formMessage.textContent = "";
    formMessage.style.color = "";
    transactionForm.reset();

    const today = new Date();
    const localDate = new Date(
        today.getTime() - today.getTimezoneOffset() * 60000
    ).toISOString().slice(0, 10);

    document.getElementById("transaction_date").value = localDate;
    transactionModal.classList.remove("hidden");
});


document.getElementById("closeModal").addEventListener("click", () => {
    transactionModal.classList.add("hidden");
    transactionForm.reset();
    editingId = null;
    saveButton.textContent = "Save transaction";
    formMessage.textContent = "";
});

transactionModal.addEventListener("click", event => {
    if (event.target === transactionModal) {
        transactionModal.classList.add("hidden");
        transactionForm.reset();
        editingId = null;
        saveButton.textContent = "Save transaction";
        formMessage.textContent = "";
    }
});


searchInput.addEventListener("input", renderTransactions);

transactionList.addEventListener("click", async event => {
    const editButton = event.target.closest(".edit-btn");
    const deleteButton = event.target.closest(".delete-btn");

    const retryButton = event.target.closest(
        ".retry-transactions-btn"
    );

    if (retryButton) {
        retryButton.disabled = true;
        retryButton.textContent = "Retrying...";
        await loadTransactions();
        return;
    }


    // EDIT TRANSACTION
    if (editButton) {
        const id = editButton.dataset.id;
        const transaction = transactions.find(
            t => String(t.id) === String(id)
        );

        if (!transaction) {
            alert("Transaction not found.");
            return;
        }

        editingId = transaction.id;
        formMessage.textContent = "";
        formMessage.style.color = "";

        transactionForm.elements.description.value =
            transaction.description;
        transactionForm.elements.amount.value = transaction.amount;
        transactionForm.elements.type.value = transaction.type;
        transactionForm.elements.category.value =
            transaction.category;
        transactionForm.elements.transaction_date.value =
            String(transaction.transaction_date).slice(0, 10);

        saveButton.textContent = "Update transaction";
        transactionModal.classList.remove("hidden");
        return;
    }

    // DELETE TRANSACTION
    if (deleteButton) {
        const id = deleteButton.dataset.id;

        const confirmed = confirm(
            "Are you sure you want to delete this transaction?"
        );

        if (!confirmed) return;

        deleteButton.disabled = true;

        try {
            const response = await fetch(
                `${API_URL}?id=${encodeURIComponent(id)}`,
                { method: "DELETE" }
            );

            if (!response.ok) {
                throw new Error(`Request failed: ${response.status}`);
            }

            await loadTransactions();
            await loadCategoryBudgets();
        } catch (error) {
            console.error("Could not delete transaction:", error);
            alert("Could not delete transaction. Please try again.");
            deleteButton.disabled = false;
        }
    }
});

transactionForm.addEventListener("submit", async event => {
    event.preventDefault();

    const formData = new FormData(transactionForm);

    const transaction = {
        description: formData.get("description").trim(),
        amount: Number(formData.get("amount")),
        type: formData.get("type"),
        category: formData.get("category"),
        transaction_date: formData.get("transaction_date")
    };

    if (!transaction.description ||
        !Number.isFinite(transaction.amount) ||
        transaction.amount <= 0 ||
        !transaction.transaction_date) {
        formMessage.style.color = "#c0392b";
        formMessage.textContent =
            "Please enter valid transaction details.";
        return;
    }

    saveButton.disabled = true;
    saveButton.textContent = editingId !== null
        ? "Updating..."
        : "Saving...";
    formMessage.textContent = "";

    try {
        const isEditing = editingId !== null;

        const url = isEditing
            ? `${API_URL}?id=${encodeURIComponent(editingId)}`
            : API_URL;

        const response = await fetch(url, {
            method: isEditing ? "PUT" : "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(transaction)
        });

        if (!response.ok) {
            const message = await response.text();
            throw new Error(
                message || `Request failed: ${response.status}`
            );
        }

       await loadTransactions();
       await loadCategoryBudgets();

       transactionModal.classList.add("hidden");
        transactionForm.reset();
        editingId = null;
        formMessage.textContent = "";

    } catch (error) {
        console.error("Could not save transaction:", error);
        formMessage.style.color = "#c0392b";
        formMessage.textContent =
            "Could not save changes. Please try again.";
    } finally {
        saveButton.disabled = false;
        saveButton.textContent = "Save transaction";
    }
});


loadTransactions();
loadBudget();

const CATEGORY_BUDGET_API_URL = "api/category-budgets";

const categoryBudgetMonthInput =
    document.getElementById("categoryBudgetMonth");
const categoryBudgetForm =
    document.getElementById("categoryBudgetForm");
const categoryBudgetCategoryInput =
    document.getElementById("categoryBudgetCategory");
const categoryBudgetAmountInput =
    document.getElementById("categoryBudgetAmount");
const categoryBudgetMessage =
    document.getElementById("categoryBudgetMessage");
const categoryBudgetSaveButton =
    document.getElementById("categoryBudgetSaveButton");
const categoryBudgetList =
    document.getElementById("categoryBudgetList");

function showCategoryBudgetMessage(message, isError = false) {
    categoryBudgetMessage.textContent = message;
    categoryBudgetMessage.style.color =
        isError ? "#c0392b" : "#16803c";
}

function escapeCategoryHTML(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

async function loadCategoryBudgets() {
    const month = categoryBudgetMonthInput.value;

    if (!month) return;

    categoryBudgetList.textContent = "Loading category budgets...";

    try {
        const response = await fetch(
            `${CATEGORY_BUDGET_API_URL}?month=${encodeURIComponent(month)}`
        );

        if (!response.ok) {
            throw new Error(`Request failed: ${response.status}`);
        }

        const data = await response.json();
        const categories = data.categories || [];

        if (categories.length === 0) {
            categoryBudgetList.innerHTML =
                '<p class="muted">No categories found.</p>';
            return;
        }

        categoryBudgetList.innerHTML = categories.map(item => {
            const budget = Number(item.budget) || 0;
            const spent = Number(item.spent) || 0;
            const remaining = budget - spent;
            const percentage = budget > 0
                ? (spent / budget) * 100
                : 0;

            const fillColor = budget <= 0
                ? "#9ca3af"
                : percentage >= 100
                    ? "#ef4444"
                    : percentage >= 80
                        ? "#f59e0b"
                        : "#10b981";

            const progressWidth = budget > 0
                ? Math.min(percentage, 100)
                : 0;

            let status;

            if (budget <= 0) {
                status = "No budget set for this category.";
            } else if (remaining < 0) {
                status = `Over budget by ${money(Math.abs(remaining))}`;
            } else {
                status = `${money(remaining)} remaining`;
            }

            return `
                <div class="category-budget-item">
                    <div class="category-budget-heading">
                        <div>
                            <strong>${escapeCategoryHTML(item.category)}</strong>
                            <div class="category-budget-meta">
                                ${money(spent)} spent of ${money(budget)}
                            </div>
                        </div>
                        <strong>${budget > 0
                            ? `${percentage.toFixed(0)}%`
                            : "—"}</strong>
                    </div>

                    <div class="category-budget-track">
                        <div
                            class="category-budget-fill"
                            style="width:${progressWidth}%;background-color:${fillColor}"
                        ></div>
                    </div>

                    <div class="category-budget-meta">
                        ${escapeCategoryHTML(status)}
                    </div>
                </div>
            `;
        }).join("");

    } catch (error) {
        console.error("Could not load category budgets:", error);
        categoryBudgetList.textContent =
            "Could not load category budgets. Check the server.";
    }
}

categoryBudgetForm.addEventListener("submit", async event => {
    event.preventDefault();

    const month = categoryBudgetMonthInput.value;
    const category = categoryBudgetCategoryInput.value;
    const budget = Number(categoryBudgetAmountInput.value);

    if (!month || !category ||
        !Number.isFinite(budget) || budget <= 0) {
        showCategoryBudgetMessage(
            "Enter a valid month, category and positive budget.",
            true
        );
        return;
    }

    categoryBudgetSaveButton.disabled = true;
    categoryBudgetSaveButton.textContent = "Saving...";
    showCategoryBudgetMessage("");

    try {
        const response = await fetch(CATEGORY_BUDGET_API_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                month,
                category,
                budget
            })
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText || `Request failed: ${response.status}`);
        }

        showCategoryBudgetMessage(
            `${category} budget saved successfully!`
        );

        await loadCategoryBudgets();

    } catch (error) {
        console.error("Could not save category budget:", error);
        showCategoryBudgetMessage(
            "Could not save budget. Check the server and try again.",
            true
        );
    } finally {
        categoryBudgetSaveButton.disabled = false;
        categoryBudgetSaveButton.textContent = "Save category budget";
    }
});

categoryBudgetMonthInput.addEventListener(
    "change",
    loadCategoryBudgets
);

categoryBudgetCategoryInput.addEventListener("change", () => {
    // Load the selected month again and fill the selected category's
    // saved budget into the form.
    loadCategoryBudgets().then(async () => {
        try {
            const month = categoryBudgetMonthInput.value;
            const response = await fetch(
                `${CATEGORY_BUDGET_API_URL}?month=${encodeURIComponent(month)}`
            );

            if (!response.ok) return;

            const data = await response.json();
            const selected = (data.categories || []).find(
                item => item.category === categoryBudgetCategoryInput.value
            );

            categoryBudgetAmountInput.value =
                selected && Number(selected.budget) > 0
                    ? selected.budget
                    : "";
        } catch (error) {
            console.error("Could not select category budget:", error);
        }
    });
});

categoryBudgetMonthInput.value = getCurrentMonth();
loadCategoryBudgets();

const logoutButton = document.getElementById("sidebarLogoutButton");

if (logoutButton) {
    logoutButton.addEventListener("click", async () => {
        const confirmed = confirm("Are you sure you want to logout?");

        if (!confirmed) return;

        logoutButton.disabled = true;

        try {
            const response = await fetch("api/auth?action=logout", {
                method: "POST",
                credentials: "same-origin"
            });

            if (!response.ok) {
                throw new Error("Logout failed");
            }

            window.location.replace("login.html");
        } catch (error) {
            console.error("Logout error:", error);
            alert("Unable to logout. Please try again.");
            logoutButton.disabled = false;
        }
    });
}

async function loadUserProfile() {
    try {
        const response = await fetch("api/auth", {
            method: "GET",
            credentials: "same-origin",
            cache: "no-store"
        });

        if (response.status === 401) {
            window.location.replace("login.html");
            return;
        }

        if (!response.ok) {
            throw new Error("Unable to load user profile");
        }

        const user = await response.json();

        const nameElement = document.getElementById("profileName");
        const greetingName = document.getElementById("greetingName");

        if (greetingName) {
            const fullName = (user.fullName || "").trim();
            greetingName.textContent = fullName
                ? fullName.split(/\s+/)[0]
                : "there";
        }
        const hour = new Date().getHours();

        const greeting =
            hour < 12 ? "Good morning" :
            hour < 17 ? "Good afternoon" :
            "Good evening";

        const heading = document.querySelector(".page-heading h1");

        if (heading && greetingName) {
            heading.firstChild.textContent = `${greeting}, `;
        }
        const emailElement = document.getElementById("profileEmail");
        const avatarElement = document.getElementById("profileAvatar");

        if (nameElement) {
            nameElement.textContent = user.fullName || "My Workspace";
        }

        if (emailElement) {
            emailElement.textContent = user.email || "Personal account";
        }

        if (avatarElement) {
            const initials = (user.fullName || "SW")
                .trim()
                .split(/\s+/)
                .slice(0, 2)
                .map(part => part.charAt(0))
                .join("")
                .toUpperCase();

            avatarElement.textContent = initials || "SW";
        }
    } catch (error) {
        console.error("Profile loading error:", error);
    }
}

loadUserProfile();
loadUserProfile

const mobileMenuButton = document.getElementById("mobileMenuButton");
const closeMobileMenu = document.getElementById("closeMobileMenu");
const mobileSidebar = document.querySelector(".sidebar");
const sidebarOverlay = document.getElementById("sidebarOverlay");
const mobileMoreButton = document.getElementById("mobileMoreButton");
const mobileMoreMenu = document.getElementById("mobileMoreMenu");
const mobileLogoutButton = document.getElementById("mobileLogoutButton");
const existingLogoutButton = document.getElementById("sidebarLogoutButton");

function openMobileSidebar() {
    mobileSidebar?.classList.add("mobile-open");
    sidebarOverlay?.classList.add("visible");
    mobileMenuButton?.setAttribute("aria-expanded", "true");
}

function closeSidebar() {
    mobileSidebar?.classList.remove("mobile-open");
    sidebarOverlay?.classList.remove("visible");
    mobileMenuButton?.setAttribute("aria-expanded", "false");
}

mobileMenuButton?.addEventListener("click", openMobileSidebar);
closeMobileMenu?.addEventListener("click", closeSidebar);
sidebarOverlay?.addEventListener("click", closeSidebar);

mobileSidebar?.querySelectorAll("nav a").forEach(link => {
    link.addEventListener("click", closeSidebar);
});

mobileMoreButton?.addEventListener("click", () => {
    const isHidden = mobileMoreMenu?.classList.contains("hidden");

    mobileMoreMenu?.classList.toggle("hidden");
    mobileMoreButton.setAttribute("aria-expanded", String(isHidden));
});

mobileLogoutButton?.addEventListener("click", () => {
    mobileMoreMenu?.classList.add("hidden");

    // Reuse the existing, working logout handler.
    if (existingLogoutButton) {
        existingLogoutButton.click();
    }
});

document.addEventListener("click", event => {
    if (
        mobileMoreMenu &&
        mobileMoreButton &&
        !mobileMoreMenu.contains(event.target) &&
        !mobileMoreButton.contains(event.target)
    ) {
        mobileMoreMenu.classList.add("hidden");
        mobileMoreButton.setAttribute("aria-expanded", "false");
    }
});

document.addEventListener("keydown", event => {
    if (event.key === "Escape") {
        closeSidebar();
        mobileMoreMenu?.classList.add("hidden");
        mobileMoreButton?.setAttribute("aria-expanded", "false");
    }
});

