package com.joshini.budget_tracker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BudgetController {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    // ---- GROUP ENDPOINTS ----

    @PostMapping("/groups")
    public Group createGroup(@RequestBody Map<String, String> body) {
        Group group = new Group(body.get("name"));
        return groupRepository.save(group);
    }

    @GetMapping("/groups")
    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    // ---- MEMBER ENDPOINTS ----

    @PostMapping("/groups/{groupId}/members")
    public Member addMember(@PathVariable Long groupId, @RequestBody Map<String, String> body) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        Member member = new Member(body.get("name"), group);
        return memberRepository.save(member);
    }

    @GetMapping("/groups/{groupId}/members")
    public List<Member> getMembersInGroup(@PathVariable Long groupId) {
        return memberRepository.findAll().stream()
                .filter(m -> m.getGroup().getId().equals(groupId))
                .toList();
    }

    // ---- EXPENSE ENDPOINTS ----

    @PostMapping("/groups/{groupId}/expenses")
    public Expense addExpense(@PathVariable Long groupId, @RequestBody Map<String, Object> body) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        Long paidById = Long.valueOf(body.get("paidById").toString());
        Member paidBy = memberRepository.findById(paidById)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        String description = (String) body.get("description");
        Double amount = Double.valueOf(body.get("amount").toString());

        Expense expense = new Expense(description, amount, group, paidBy);
        Expense savedExpense = expenseRepository.save(expense);

        // Get the list of member IDs this expense should be split among
        @SuppressWarnings("unchecked")
        List<Object> splitAmongIds = (List<Object>) body.get("splitAmong");

        if (splitAmongIds == null || splitAmongIds.isEmpty()) {
            // Default: split among everyone in the group if nothing specified
            List<Member> allMembers = memberRepository.findAll().stream()
                    .filter(m -> m.getGroup().getId().equals(groupId))
                    .toList();
            for (Member m : allMembers) {
                expenseSplitRepository.save(new ExpenseSplit(savedExpense, m));
            }
        } else {
            // Split only among the specifically selected members
            for (Object idObj : splitAmongIds) {
                Long memberId = Long.valueOf(idObj.toString());
                Member m = memberRepository.findById(memberId)
                        .orElseThrow(() -> new RuntimeException("Member not found"));
                expenseSplitRepository.save(new ExpenseSplit(savedExpense, m));
            }
        }

        return savedExpense;
    }

    @GetMapping("/groups/{groupId}/expenses")
    public List<Expense> getExpensesInGroup(@PathVariable Long groupId) {
        return expenseRepository.findAll().stream()
                .filter(e -> e.getGroup().getId().equals(groupId))
                .toList();
    }


// ---- BALANCE CALCULATION ----

    @GetMapping("/groups/{groupId}/balances")
    public Map<String, Double> getBalances(@PathVariable Long groupId) {
        List<Member> members = memberRepository.findAll().stream()
                .filter(m -> m.getGroup().getId().equals(groupId))
                .toList();

        List<Expense> expenses = expenseRepository.findAll().stream()
                .filter(e -> e.getGroup().getId().equals(groupId))
                .toList();

        Map<String, Double> balances = new HashMap<>();
        for (Member member : members) {
            balances.put(member.getName(), 0.0);
        }

        for (Expense expense : expenses) {
            List<ExpenseSplit> splits = expenseSplitRepository.findAll().stream()
                    .filter(s -> s.getExpense().getId().equals(expense.getId()))
                    .toList();

            if (splits.isEmpty()) continue;

            double shareEach = expense.getAmount() / splits.size();

            // Credit the payer for the full amount they paid
            String payerName = expense.getPaidBy().getName();
            balances.put(payerName, balances.get(payerName) + expense.getAmount());

            // Debit each person their share of this specific expense
            for (ExpenseSplit split : splits) {
                String memberName = split.getMember().getName();
                balances.put(memberName, balances.get(memberName) - shareEach);
            }
        }

        Map<String, Double> rounded = new HashMap<>();
        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            rounded.put(entry.getKey(), Math.round(entry.getValue() * 100.0) / 100.0);
        }

        return rounded;
    }




    @GetMapping("/groups/{groupId}/settlement")
    public List<String> getSettlement(@PathVariable Long groupId) {
        Map<String, Double> balances = getBalances(groupId);

        List<Map.Entry<String, Double>> creditors = new ArrayList<>();
        List<Map.Entry<String, Double>> debtors = new ArrayList<>();

        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            if (entry.getValue() > 0) {
                creditors.add(entry);
            } else if (entry.getValue() < 0) {
                debtors.add(entry);
            }
        }

        List<String> settlements = new ArrayList<>();
        int i = 0, j = 0;

        while (i < debtors.size() && j < creditors.size()) {
            String debtorName = debtors.get(i).getKey();
            String creditorName = creditors.get(j).getKey();

            double debtAmount = -debtors.get(i).getValue();
            double creditAmount = creditors.get(j).getValue();

            double settledAmount = Math.min(debtAmount, creditAmount);

            settlements.add(debtorName + " pays " + creditorName + " ₹" + settledAmount);

            debtors.set(i, Map.entry(debtorName, debtors.get(i).getValue() + settledAmount));
            creditors.set(j, Map.entry(creditorName, creditors.get(j).getValue() - settledAmount));

            if (Math.round(debtors.get(i).getValue() * 100.0) == 0) i++;
            if (Math.round(creditors.get(j).getValue() * 100.0) == 0) j++;
        }

        return settlements;
    }
}