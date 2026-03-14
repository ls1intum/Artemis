#!/usr/bin/env node

/**
 * Demo Data Setup Script for Master Thesis Presentation
 *
 * Creates a realistic "Data Structures and Algorithms" course with:
 * - Named characters: Prof. Sofia (admin/instructor), Luca (student), Tom (instructor)
 * - ~50 students for a realistic class size
 * - Programming exercises including "Binary Search Trees" (for global search demo)
 * - Lectures with content about trees, sorting, graphs (for search demo)
 * - Modeling, text, and quiz exercises
 * - Communication posts in the course channel
 *
 * Demo flow (from presentation):
 * 1. Passkey registration — Prof. Sofia registers a passkey
 * 2. Passkey login — One-touch login
 * 3. Locked indicator — Privileged admin action with lock icon (password session)
 * 4. Global search — Cmd+K, "binary search trees" → results across exercises and lectures
 *
 * Usage:
 *   node demo-setup.mjs [options]
 *
 * Options:
 *   --server-url=<url>         Server URL (default: http://localhost:8080)
 *   --admin-user=<username>    Admin username (default: artemis_admin)
 *   --admin-password=<pass>    Admin password (default: artemis_admin)
 *   --student-count=<n>        Number of generic students (default: 50)
 *   --skip-participations      Skip student participations and communication
 */

import { HttpClient, createMultipartFormData } from '../setup-course/lib/http-client.mjs';
import { authenticate } from '../setup-course/lib/auth.mjs';

// ─── CLI Parsing ──────────────────────────────────────────────────────────────

function parseArgs(argv) {
    const args = {};
    for (const arg of argv) {
        if (arg.startsWith('--')) {
            const [key, ...rest] = arg.slice(2).split('=');
            args[key] = rest.length > 0 ? rest.join('=') : '';
        }
    }
    return args;
}

const args = parseArgs(process.argv.slice(2));

const config = {
    serverUrl: args['server-url'] || process.env.ARTEMIS_SERVER_URL || 'http://localhost:8080',
    adminUser: args['admin-user'] || process.env.ARTEMIS_ADMIN_USER || 'artemis_admin',
    adminPassword: args['admin-password'] || process.env.ARTEMIS_ADMIN_PASSWORD || 'artemis_admin',
    studentCount: parseInt(args['student-count'] || '50', 10),
    skipParticipations: args['skip-participations'] === 'true' || args['skip-participations'] === '',
};

const STUDENT_PASSWORD = 'Password123!';

// ─── Course Configuration ─────────────────────────────────────────────────────

const COURSE_TITLE = 'Data Structures and Algorithms';
const COURSE_SHORT_NAME = 'DSA2026';

// ─── Named Demo Characters ────────────────────────────────────────────────────

const DEMO_USERS = {
    sofia: {
        login: 'prof_sofia',
        firstName: 'Sofia',
        lastName: 'Meier',
        email: 'sofia.meier@tum.test',
        password: STUDENT_PASSWORD,
        role: 'instructor',       // also added as admin
        description: 'Professor — manages the course, demonstrates passkey',
    },
    luca: {
        login: 'luca',
        firstName: 'Luca',
        lastName: 'Bauer',
        email: 'luca.bauer@tum.test',
        password: STUDENT_PASSWORD,
        role: 'student',
        description: 'Student — searches for "binary search trees"',
    },
    tom: {
        login: 'tom',
        firstName: 'Tom',
        lastName: 'Schmidt',
        email: 'tom.schmidt@tum.test',
        password: STUDENT_PASSWORD,
        role: 'instructor',
        description: 'Colleague — fell for phishing, compromised admin',
    },
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

const timestamp = Date.now();

async function createUser(client, userData) {
    const user = {
        activated: true,
        login: userData.login,
        email: userData.email,
        firstName: userData.firstName,
        lastName: userData.lastName,
        langKey: 'en',
        password: userData.password,
    };

    try {
        const response = await client.post('/api/core/admin/users', user);
        return { ...response.data, isNew: true };
    } catch (error) {
        if (error.response?.status === 400 || error.response?.status === 409) {
            const detail = error.response?.data?.message || error.response?.data?.title || '';
            // If the error is about email already in use (not login), the login might be free
            // but a different user has the same email. Log this for debugging.
            if (detail) {
                console.log(`    Note: user creation for "${userData.login}" returned 400: ${detail}`);
            }
            try {
                const existing = await client.get(`/api/core/admin/users/${userData.login}`);
                return { ...existing.data, isNew: false };
            } catch (fetchError) {
                console.log(`    Warning: could not fetch existing user "${userData.login}" (${fetchError.response?.status || fetchError.message}). The login may be available but the email "${userData.email}" may conflict with another user.`);
                return { login: userData.login, isNew: false, fetchFailed: true };
            }
        }
        throw error;
    }
}

async function addUserToCourse(client, courseId, group, username) {
    try {
        await client.post(`/api/core/courses/${courseId}/${group}/${username}`);
    } catch (error) {
        if (error.response?.status === 400) {
            return; // already in group, that's fine
        }
        if (error.response?.status === 404) {
            console.log(`    Warning: Could not add "${username}" to ${group} — user not found (404). Skipping.`);
            return;
        }
        throw error;
    }
}

async function grantAdminRole(client, username) {
    // Fetch the user, add ADMIN authority, update
    try {
        const resp = await client.get(`/api/core/admin/users/${username}`);
        const user = resp.data;
        const authorities = user.authorities || [];
        if (!authorities.includes('ROLE_ADMIN')) {
            authorities.push('ROLE_ADMIN');
        }
        user.authorities = authorities;
        await client.put('/api/core/admin/users', user);
        console.log(`    Granted ADMIN role to ${username}`);
    } catch (error) {
        console.log(`    Could not grant ADMIN role to ${username}: ${error.message}`);
    }
}

// ─── Course Creation ──────────────────────────────────────────────────────────

async function createCourse(client) {
    const now = new Date();
    const startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000); // 30 days ago
    const endDate = new Date(now.getTime() + 90 * 24 * 60 * 60 * 1000);   // 90 days from now

    const course = {
        title: COURSE_TITLE,
        shortName: COURSE_SHORT_NAME,
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString(),
        complaintsEnabled: true,
        requestMoreFeedbackEnabled: true,
        maxComplaints: 3,
        maxTeamComplaints: 3,
        maxComplaintTimeDays: 7,
        maxComplaintTextLimit: 2000,
        maxComplaintResponseTextLimit: 2000,
        maxRequestMoreFeedbackTimeDays: 7,
        courseInformationSharingConfiguration: 'COMMUNICATION_AND_MESSAGING',
        enrollmentEnabled: true,
        accuracyOfScores: 1,
        onlineCourse: false,
        timeZone: 'Europe/Berlin',
        semester: 'SS2026',
        description: 'This course covers fundamental data structures and algorithms including arrays, linked lists, trees, graphs, sorting, and searching. Students learn to analyze time and space complexity and apply algorithmic thinking to solve computational problems.',
        defaultChannelName: 'general',
    };

    const { body, contentType } = createMultipartFormData({ course });

    const response = await client.post('/api/core/admin/courses', body, {
        headers: { 'Content-Type': contentType },
        contentType: 'multipart',
    });

    return response.data;
}

// ─── Exercise Definitions ─────────────────────────────────────────────────────

const PROGRAMMING_EXERCISES = [
    {
        title: 'Binary Search Trees',
        shortName: `BST${timestamp}`,
        programmingLanguage: 'JAVA',
        projectType: 'PLAIN_GRADLE',
        packageName: 'de.tum.cit.dsa.bst',
        staticCodeAnalysisEnabled: true,
        problemStatement: `# Binary Search Trees

## Task Description
Implement a Binary Search Tree (BST) that supports insertion, search, and traversal operations.

## Requirements
1. Implement the \`insert(int key)\` method in the \`BinarySearchTree\` class
2. Implement the \`search(int key)\` method that returns \`true\` if the key exists
3. Implement \`inorderTraversal()\` that returns a sorted list of all elements
4. Implement \`delete(int key)\` to remove a node while maintaining BST properties

## Background
A binary search tree is a rooted binary tree data structure where each node has a key greater than all keys in its left subtree and less than all keys in its right subtree. This property enables efficient searching, insertion, and deletion in O(log n) average time.

## Example
\`\`\`java
BinarySearchTree bst = new BinarySearchTree();
bst.insert(5);
bst.insert(3);
bst.insert(7);
bst.insert(1);
bst.search(3);   // returns true
bst.search(4);   // returns false
bst.inorderTraversal(); // returns [1, 3, 5, 7]
\`\`\`

## Hints
- Handle the base case of an empty tree
- For deletion, consider the three cases: leaf node, one child, two children
- Use recursion for clean implementations
- Think about balanced vs. unbalanced trees and their performance implications`,
    },
    {
        title: 'Linked List Implementation',
        shortName: `LList${timestamp}`,
        programmingLanguage: 'JAVA',
        projectType: 'PLAIN_GRADLE',
        packageName: 'de.tum.cit.dsa.linkedlist',
        staticCodeAnalysisEnabled: false,
        problemStatement: `# Linked List Implementation

## Task Description
Implement a generic singly linked list with common operations.

## Requirements
1. Implement \`add(T element)\` to append an element
2. Implement \`get(int index)\` to retrieve an element by index
3. Implement \`remove(int index)\` to remove and return an element
4. Implement \`size()\` and \`isEmpty()\`
5. Implement \`reverse()\` to reverse the list in-place

## Example
\`\`\`java
LinkedList<Integer> list = new LinkedList<>();
list.add(1);
list.add(2);
list.add(3);
list.get(1);    // returns 2
list.size();    // returns 3
list.reverse(); // list is now [3, 2, 1]
\`\`\`

## Hints
- Use a Node inner class with data and next pointer
- Track both head and size for O(1) size queries
- Handle edge cases: empty list, single element, index out of bounds`,
    },
    {
        title: 'Graph Traversal Algorithms',
        shortName: `Graph${timestamp}`,
        programmingLanguage: 'JAVA',
        projectType: 'PLAIN_GRADLE',
        packageName: 'de.tum.cit.dsa.graph',
        staticCodeAnalysisEnabled: false,
        problemStatement: `# Graph Traversal Algorithms

## Task Description
Implement breadth-first search (BFS) and depth-first search (DFS) for an adjacency list graph.

## Requirements
1. Implement the \`Graph\` class with an adjacency list representation
2. Implement \`addEdge(int from, int to)\` for directed edges
3. Implement \`bfs(int start)\` returning nodes in BFS order
4. Implement \`dfs(int start)\` returning nodes in DFS order
5. Implement \`hasPath(int from, int to)\` using BFS

## Example
\`\`\`java
Graph g = new Graph(5);
g.addEdge(0, 1);
g.addEdge(0, 2);
g.addEdge(1, 3);
g.addEdge(2, 4);
g.bfs(0);          // returns [0, 1, 2, 3, 4]
g.hasPath(0, 4);   // returns true
\`\`\`

## Hints
- Use a Queue (LinkedList) for BFS
- Use a Stack or recursion for DFS
- Track visited nodes to avoid cycles`,
    },
    {
        title: 'Sorting Algorithms',
        shortName: `Sort${timestamp}`,
        programmingLanguage: 'JAVA',
        projectType: 'PLAIN_GRADLE',
        packageName: 'de.tum.cit.dsa.sorting',
        staticCodeAnalysisEnabled: true,
        problemStatement: `# Sorting Algorithms

## Task Description
Implement and compare multiple sorting algorithms.

## Requirements
1. Implement \`mergeSort(int[] array)\` — O(n log n) divide-and-conquer
2. Implement \`quickSort(int[] array)\` — O(n log n) average-case
3. Implement \`insertionSort(int[] array)\` — O(n^2) simple sort
4. All methods should sort the array in ascending order

## Example
\`\`\`java
int[] array = {38, 27, 43, 3, 9, 82, 10};
Sorting.mergeSort(array);
// Result: {3, 9, 10, 27, 38, 43, 82}
\`\`\`

## Hints
- MergeSort: split array, sort halves recursively, merge
- QuickSort: choose pivot, partition, recurse on partitions
- Consider stability and in-place sorting trade-offs`,
    },
];

const MODELING_EXERCISES = [
    {
        title: 'UML Class Diagram: Tree Structures',
        shortName: `UMLTree${timestamp}`,
        diagramType: 'ClassDiagram',
        difficulty: 'MEDIUM',
        problemStatement: `# UML Class Diagram: Tree Structures

## Task Description
Design a UML class diagram that models the class hierarchy for different tree data structures.

## Requirements
1. Model the abstract base class \`Tree<T>\` with common operations
2. Model \`BinarySearchTree<T>\` extending \`Tree<T>\`
3. Model \`AVLTree<T>\` extending \`BinarySearchTree<T>\`
4. Model the \`Node<T>\` class used by tree implementations
5. Show associations, inheritance, and key methods

## Evaluation Criteria
- Correct use of UML notation
- Proper inheritance hierarchy
- Complete method signatures
- Appropriate visibility modifiers`,
    },
    {
        title: 'Activity Diagram: Search Algorithm Flow',
        shortName: `ActSearch${timestamp}`,
        diagramType: 'ActivityDiagram',
        difficulty: 'EASY',
        problemStatement: `# Activity Diagram: Search Algorithm Flow

## Task Description
Create an activity diagram showing the flow of a binary search algorithm.

## Requirements
1. Show the input: sorted array and target value
2. Model the iterative comparison steps
3. Include decision points for found/not found
4. Show the narrowing of search bounds (left/right pointers)
5. Model the termination condition

## Evaluation Criteria
- Correct activity diagram notation
- Complete flow coverage
- Proper decision nodes and merge points`,
    },
];

const TEXT_EXERCISES = [
    {
        title: 'Essay: Comparing Sorting Algorithms',
        shortName: `EssaySort${timestamp}`,
        difficulty: 'MEDIUM',
        problemStatement: `# Essay: Comparing Sorting Algorithms

## Task Description
Write a comprehensive comparison of at least three sorting algorithms.

## Requirements
1. **Introduction**: Explain why sorting is fundamental in computer science
2. **Algorithm Descriptions**: Describe the mechanics of at least three sorting algorithms (e.g., MergeSort, QuickSort, HeapSort)
3. **Complexity Analysis**: Compare time and space complexity (best, average, worst case)
4. **Practical Considerations**: Discuss when to use which algorithm in practice
5. **Conclusion**: Summarize the trade-offs

## Evaluation Criteria
- Technical accuracy of complexity analysis
- Clarity of algorithm descriptions
- Quality of practical examples
- Overall structure and writing quality`,
    },
    {
        title: 'Summary: Applications of Binary Search Trees',
        shortName: `SumBST${timestamp}`,
        difficulty: 'EASY',
        problemStatement: `# Summary: Applications of Binary Search Trees

## Task Description
Write a summary discussing real-world applications of binary search trees.

## Requirements
1. Describe at least 3 real-world applications of BSTs
2. Explain why BSTs are suitable for each application
3. Discuss limitations and alternatives (e.g., hash tables, B-trees)
4. Include complexity analysis for common operations

## Evaluation Criteria
- Breadth of applications covered
- Depth of analysis
- Connection between theory and practice`,
    },
];

const QUIZ_EXERCISES = [
    {
        title: 'Data Structures Fundamentals Quiz',
        shortName: `DSQuiz${timestamp}`,
        duration: 30,
        questions: [
            {
                type: 'multiple-choice',
                title: 'BST Search Complexity',
                text: 'What is the average-case time complexity of searching in a balanced binary search tree?',
                answerOptions: [
                    { text: 'O(log n)', isCorrect: true },
                    { text: 'O(n)', isCorrect: false },
                    { text: 'O(n log n)', isCorrect: false },
                    { text: 'O(1)', isCorrect: false },
                ],
                points: 1,
                singleChoice: true,
            },
            {
                type: 'multiple-choice',
                title: 'Linked List Properties',
                text: 'Which properties are true for a singly linked list? (Select all that apply)',
                answerOptions: [
                    { text: 'O(1) insertion at the head', isCorrect: true },
                    { text: 'O(1) access by index', isCorrect: false },
                    { text: 'Dynamic size (no fixed capacity)', isCorrect: true },
                    { text: 'O(n) search for an element', isCorrect: true },
                ],
                points: 3,
                singleChoice: false,
            },
            {
                type: 'multiple-choice',
                title: 'Stack vs Queue',
                text: 'Which data structure uses FIFO (First In, First Out) ordering?',
                answerOptions: [
                    { text: 'Queue', isCorrect: true },
                    { text: 'Stack', isCorrect: false },
                    { text: 'Priority Queue', isCorrect: false },
                    { text: 'Deque', isCorrect: false },
                ],
                points: 1,
                singleChoice: true,
            },
            {
                type: 'short-answer',
                title: 'Tree Terminology',
                text: 'In a binary tree, a node with no children is called a [-spot 1] node.',
                solutions: ['leaf'],
                points: 1,
            },
        ],
    },
    {
        title: 'Sorting Algorithms Quiz',
        shortName: `SortQuiz${timestamp}`,
        duration: 20,
        questions: [
            {
                type: 'multiple-choice',
                title: 'MergeSort Complexity',
                text: 'What is the worst-case time complexity of MergeSort?',
                answerOptions: [
                    { text: 'O(n log n)', isCorrect: true },
                    { text: 'O(n^2)', isCorrect: false },
                    { text: 'O(n)', isCorrect: false },
                    { text: 'O(log n)', isCorrect: false },
                ],
                points: 1,
                singleChoice: true,
            },
            {
                type: 'multiple-choice',
                title: 'Stable Sorting',
                text: 'Which of the following sorting algorithms are stable? (Select all that apply)',
                answerOptions: [
                    { text: 'MergeSort', isCorrect: true },
                    { text: 'QuickSort', isCorrect: false },
                    { text: 'InsertionSort', isCorrect: true },
                    { text: 'HeapSort', isCorrect: false },
                ],
                points: 2,
                singleChoice: false,
            },
            {
                type: 'multiple-choice',
                title: 'QuickSort Pivot',
                text: 'What happens to QuickSort performance when the pivot is always the smallest element?',
                answerOptions: [
                    { text: 'It degrades to O(n^2)', isCorrect: true },
                    { text: 'It stays O(n log n)', isCorrect: false },
                    { text: 'It improves to O(n)', isCorrect: false },
                    { text: 'The algorithm fails', isCorrect: false },
                ],
                points: 1,
                singleChoice: true,
            },
        ],
    },
];

// ─── Lecture Definitions ──────────────────────────────────────────────────────

const LECTURES = [
    {
        title: 'Introduction to Data Structures',
        description: 'Overview of fundamental data structures: arrays, linked lists, stacks, and queues.',
        textUnits: [
            {
                name: 'Arrays and Dynamic Arrays',
                content: `# Arrays and Dynamic Arrays

## Static Arrays
An array is a contiguous block of memory storing elements of the same type.

### Properties
- **Random access**: O(1) time to access any element by index
- **Fixed size**: Capacity determined at creation
- **Cache-friendly**: Elements stored contiguously in memory

### Common Operations
| Operation     | Time Complexity |
|---------------|----------------|
| Access        | O(1)           |
| Search        | O(n)           |
| Insert (end)  | O(1) amortized |
| Insert (mid)  | O(n)           |
| Delete        | O(n)           |

## Dynamic Arrays (ArrayList in Java)
Dynamic arrays automatically resize when capacity is exceeded.

\`\`\`java
ArrayList<Integer> list = new ArrayList<>();
list.add(42);    // O(1) amortized
list.get(0);     // O(1)
list.remove(0);  // O(n)
\`\`\`

### Amortized Analysis
When the array is full, a new array of double the size is allocated and elements are copied. This "doubling strategy" ensures that the amortized cost of insertion remains O(1).`,
            },
            {
                name: 'Stacks and Queues',
                content: `# Stacks and Queues

## Stack (LIFO — Last In, First Out)
A stack supports two primary operations:
- **push(x)**: Add element to the top — O(1)
- **pop()**: Remove and return the top element — O(1)

### Applications
- Function call stack (recursion)
- Undo/redo operations
- Expression evaluation and syntax parsing
- Depth-first search (DFS)

\`\`\`java
Stack<Integer> stack = new Stack<>();
stack.push(1);
stack.push(2);
stack.pop(); // returns 2
\`\`\`

## Queue (FIFO — First In, First Out)
A queue supports:
- **enqueue(x)**: Add element to the back — O(1)
- **dequeue()**: Remove and return the front element — O(1)

### Applications
- Breadth-first search (BFS)
- Task scheduling
- Print job queues
- Message buffering

\`\`\`java
Queue<Integer> queue = new LinkedList<>();
queue.offer(1);
queue.offer(2);
queue.poll(); // returns 1
\`\`\``,
            },
        ],
    },
    {
        title: 'Trees and Binary Search Trees',
        description: 'In-depth coverage of tree data structures with focus on binary search trees, traversals, and balanced trees.',
        textUnits: [
            {
                name: 'Introduction to Trees',
                content: `# Introduction to Trees

## What is a Tree?
A tree is a hierarchical data structure consisting of nodes connected by edges. It is an acyclic connected graph.

### Terminology
- **Root**: The topmost node (no parent)
- **Parent/Child**: A node directly connected above/below another
- **Leaf**: A node with no children
- **Depth**: Distance from the root to a node
- **Height**: Maximum depth of any node in the tree
- **Subtree**: A node and all its descendants

## Binary Trees
A binary tree is a tree where each node has at most two children (left and right).

### Types of Binary Trees
1. **Full Binary Tree**: Every node has 0 or 2 children
2. **Complete Binary Tree**: All levels filled except possibly the last
3. **Perfect Binary Tree**: All internal nodes have 2 children, all leaves at same depth
4. **Balanced Binary Tree**: Height difference between left and right subtrees ≤ 1

## Tree Traversals

### Inorder (Left → Root → Right)
Visits nodes in sorted order for BSTs.

### Preorder (Root → Left → Right)
Useful for creating a copy of the tree.

### Postorder (Left → Right → Root)
Useful for deleting a tree.

### Level-order (BFS)
Visits nodes level by level.`,
            },
            {
                name: 'Binary Search Trees (BST)',
                content: `# Binary Search Trees

## Definition
A binary search tree is a binary tree where for every node:
- All keys in the **left subtree** are **less than** the node's key
- All keys in the **right subtree** are **greater than** the node's key

This property enables efficient searching, insertion, and deletion.

## Operations

### Search — O(h) where h = height
\`\`\`java
public boolean search(Node node, int key) {
    if (node == null) return false;
    if (key == node.key) return true;
    if (key < node.key) return search(node.left, key);
    return search(node.right, key);
}
\`\`\`

### Insertion — O(h)
Navigate to the correct position and insert as a leaf.

### Deletion — O(h)
Three cases:
1. **Leaf node**: Simply remove
2. **One child**: Replace node with its child
3. **Two children**: Replace with inorder successor (smallest in right subtree)

## Complexity Analysis
| Operation | Average | Worst Case |
|-----------|---------|------------|
| Search    | O(log n)| O(n)       |
| Insert    | O(log n)| O(n)       |
| Delete    | O(log n)| O(n)       |

The worst case occurs when the tree degenerates into a linked list (e.g., inserting sorted data).

## Balanced BSTs
To guarantee O(log n) operations, we use self-balancing trees:
- **AVL Trees**: Maintain balance factor ≤ 1 using rotations
- **Red-Black Trees**: Maintain approximate balance using coloring rules
- **B-Trees**: Used in databases and file systems for disk-efficient operations`,
            },
            {
                name: 'AVL Trees and Rotations',
                content: `# AVL Trees

## Motivation
Unbalanced BSTs can degrade to O(n) performance. AVL trees (named after Adelson-Velsky and Landis) maintain balance automatically.

## Balance Factor
For each node: balance factor = height(left subtree) - height(right subtree)
An AVL tree maintains |balance factor| ≤ 1 for every node.

## Rotations

### Left Rotation
Used when right subtree is too tall (balance factor = -2).

### Right Rotation
Used when left subtree is too tall (balance factor = 2).

### Left-Right Rotation
Double rotation for left-right imbalance.

### Right-Left Rotation
Double rotation for right-left imbalance.

## Complexity
All operations (search, insert, delete) are guaranteed O(log n) because the tree height is always O(log n).`,
            },
        ],
    },
    {
        title: 'Sorting Algorithms',
        description: 'Comprehensive coverage of comparison-based and non-comparison sorting algorithms.',
        textUnits: [
            {
                name: 'Comparison-Based Sorting',
                content: `# Comparison-Based Sorting Algorithms

## Lower Bound
Any comparison-based sorting algorithm requires at least Ω(n log n) comparisons in the worst case. This is a fundamental theorem in computer science.

## MergeSort — O(n log n)

### Algorithm
1. Divide the array into two halves
2. Recursively sort each half
3. Merge the two sorted halves

### Properties
- **Stable**: Preserves relative order of equal elements
- **Not in-place**: Requires O(n) additional space
- **Guaranteed O(n log n)**: No worst-case degradation

\`\`\`java
void mergeSort(int[] arr, int left, int right) {
    if (left < right) {
        int mid = (left + right) / 2;
        mergeSort(arr, left, mid);
        mergeSort(arr, mid + 1, right);
        merge(arr, left, mid, right);
    }
}
\`\`\`

## QuickSort — O(n log n) average

### Algorithm
1. Choose a pivot element
2. Partition: elements < pivot go left, elements > pivot go right
3. Recursively sort the partitions

### Properties
- **Not stable** (in typical implementations)
- **In-place**: O(log n) stack space
- **Average O(n log n)**, worst case O(n²) with poor pivot choice

## InsertionSort — O(n²)
Simple, efficient for small or nearly-sorted arrays.
Used as a subroutine in hybrid algorithms like TimSort.`,
            },
            {
                name: 'Non-Comparison Sorting and Analysis',
                content: `# Non-Comparison Sorting

## Counting Sort — O(n + k)
Works by counting occurrences of each value. Efficient when range k is small.

## Radix Sort — O(d × (n + k))
Sorts by individual digits, from least significant to most significant.

## Bucket Sort — O(n + k)
Distributes elements into buckets, sorts each bucket, then concatenates.

## Comparison Summary

| Algorithm      | Best    | Average   | Worst     | Space  | Stable |
|---------------|---------|-----------|-----------|--------|--------|
| MergeSort     | O(n log n) | O(n log n) | O(n log n) | O(n) | Yes |
| QuickSort     | O(n log n) | O(n log n) | O(n²)   | O(log n) | No |
| HeapSort      | O(n log n) | O(n log n) | O(n log n) | O(1) | No |
| InsertionSort | O(n)    | O(n²)    | O(n²)     | O(1)   | Yes    |
| CountingSort  | O(n+k)  | O(n+k)   | O(n+k)    | O(k)   | Yes    |
| RadixSort     | O(dn)   | O(dn)    | O(dn)     | O(n+k) | Yes    |`,
            },
        ],
    },
    {
        title: 'Graph Algorithms',
        description: 'Graph representations, traversals, shortest paths, and minimum spanning trees.',
        textUnits: [
            {
                name: 'Graph Representations and Traversals',
                content: `# Graph Representations

## Adjacency Matrix
A 2D boolean array where \`matrix[i][j] = true\` if edge (i,j) exists.
- Space: O(V²)
- Edge lookup: O(1)
- Best for dense graphs

## Adjacency List
An array of lists where \`adj[i]\` contains neighbors of vertex i.
- Space: O(V + E)
- Edge lookup: O(degree)
- Best for sparse graphs

## Breadth-First Search (BFS)
Explores vertices level by level using a queue.

\`\`\`java
void bfs(int start) {
    Queue<Integer> queue = new LinkedList<>();
    boolean[] visited = new boolean[V];
    queue.offer(start);
    visited[start] = true;
    while (!queue.isEmpty()) {
        int v = queue.poll();
        for (int neighbor : adj[v]) {
            if (!visited[neighbor]) {
                visited[neighbor] = true;
                queue.offer(neighbor);
            }
        }
    }
}
\`\`\`

## Depth-First Search (DFS)
Explores as deep as possible before backtracking, using recursion or a stack.

### Applications
- Cycle detection
- Topological sorting
- Connected components
- Path finding`,
            },
            {
                name: 'Shortest Paths and Minimum Spanning Trees',
                content: `# Shortest Path Algorithms

## Dijkstra's Algorithm — O((V + E) log V)
Finds shortest paths from a single source in graphs with non-negative weights.

## Bellman-Ford — O(V × E)
Handles negative edge weights. Can detect negative cycles.

## Floyd-Warshall — O(V³)
All-pairs shortest paths using dynamic programming.

# Minimum Spanning Trees

## Kruskal's Algorithm — O(E log E)
1. Sort edges by weight
2. Add edges that don't create a cycle (use Union-Find)

## Prim's Algorithm — O((V + E) log V)
1. Start from any vertex
2. Greedily add the cheapest edge connecting the tree to a new vertex

Both algorithms produce a tree spanning all vertices with minimum total edge weight.`,
            },
        ],
    },
];

// ─── Communication Posts (DSA-themed) ─────────────────────────────────────────

const DSA_POSTS = [
    {
        title: 'Welcome to Data Structures and Algorithms!',
        content: 'Welcome everyone! I am Prof. Sofia Meier and I will be teaching this course. Please feel free to ask questions here. Our first exercise on linked lists is already available — make sure to start early!',
        author: 'sofia',
    },
    {
        title: 'Question about binary search trees',
        content: 'Hi everyone, I am struggling with the deletion operation in the binary search tree exercise. When the node to delete has two children, how do I find the inorder successor? Can someone explain the algorithm step by step?',
        author: 'luca',
    },
    {
        title: 'Tips for the sorting algorithms exercise',
        content: 'For the sorting exercise: I found it helpful to first implement MergeSort since the concept is more intuitive (divide, sort, merge). Once you understand the recursive approach, QuickSort becomes easier to grasp. Good luck!',
        author: 'tom',
    },
    {
        title: 'Study group for exam preparation',
        content: 'Is anyone interested in forming a study group to prepare for the exam? We could meet in the library or online. I think reviewing tree traversals and graph algorithms together would be really helpful.',
        author: 'luca',
    },
    {
        title: 'Office hours this week',
        content: 'Reminder: My office hours this week are Tuesday 14:00-16:00 and Thursday 10:00-12:00 in room 01.10.033. Bring your questions about the graph traversal exercise — I have seen several students struggling with BFS vs DFS.',
        author: 'sofia',
    },
];

const DSA_ANSWERS = [
    'For BST deletion with two children: find the smallest node in the right subtree (go right once, then left as far as possible). Replace the deleted node\'s key with that value, then delete the successor node. It will have at most one child!',
    'I also recommend drawing the tree on paper before coding. Visualizing the rotations helped me a lot with AVL trees.',
    'Great idea about the study group! I am in. Maybe we could also go through past exam questions together?',
    'Thanks for the MergeSort tip! The recursive structure clicked for me when I thought of it as a post-order tree traversal.',
    'Could you also cover the time complexity analysis for balanced vs unbalanced BSTs in office hours? I am confused about when O(log n) vs O(n) applies.',
    'Pro tip: use the debugger to step through your sorting code with a small array (like 5 elements). It makes understanding the partitioning in QuickSort much clearer.',
    'Count me in for the study group! I am especially struggling with Dijkstra\'s algorithm and would appreciate going through it together.',
    'Thank you Prof. Meier! The office hours were very helpful. For others: make sure you understand the difference between BFS (uses queue) and DFS (uses stack/recursion) — it was a key exam topic last year.',
];

// ─── Quiz Helper ──────────────────────────────────────────────────────────────

function generateTempId() {
    return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
}

function createQuizQuestion(config) {
    const base = {
        title: config.title,
        text: config.text,
        points: config.points,
        exportQuiz: false,
        invalid: false,
        randomizeOrder: false,
    };

    if (config.type === 'multiple-choice') {
        return {
            ...base,
            type: 'multiple-choice',
            answerOptions: config.answerOptions.map((opt) => ({
                text: opt.text,
                isCorrect: opt.isCorrect,
                explanation: null,
                invalid: false,
            })),
            singleChoice: config.singleChoice,
            scoringType: 'ALL_OR_NOTHING',
        };
    } else if (config.type === 'short-answer') {
        const spots = [];
        const solutions = [];
        const correctMappings = [];

        config.solutions.forEach((solutionText, i) => {
            const spotTempId = generateTempId();
            const solutionTempId = generateTempId();
            spots.push({ tempID: spotTempId, invalid: false, width: 15, spotNr: i + 1 });
            solutions.push({ tempID: solutionTempId, invalid: false, text: solutionText });
            correctMappings.push({ spotTempId, solutionTempId, invalid: false });
        });

        return {
            ...base,
            type: 'short-answer',
            spots,
            solutions,
            correctMappings,
            matchLetterCase: false,
            similarityValue: 85,
            scoringType: 'PROPORTIONAL_WITHOUT_PENALTY',
        };
    }

    return base;
}

// ─── Additional Courses (lighter content, Sofia is instructor) ────────────────

const ADDITIONAL_COURSES = [
    {
        title: 'Introduction to Informatics',
        shortName: 'IntroInf2026',
        semester: 'SS2026',
        description: 'Introductory course covering the basics of computer science, programming in Python, and computational thinking.',
        studentCount: 15,
        exercises: [
            {
                type: 'programming',
                title: 'Hello World in Python',
                shortName: `HelloPy${timestamp}`,
                programmingLanguage: 'PYTHON',
                projectType: null,
                packageName: null,
                problemStatement: `# Hello World in Python\n\nWrite a Python program that prints "Hello, World!" to the console.\n\n## Requirements\n1. Create a function \`greet(name)\` that returns a greeting string\n2. Handle the case where no name is provided (default to "World")\n3. Print the result to the console`,
            },
            {
                type: 'text',
                title: 'Essay: What is Computer Science?',
                shortName: `EssayCS${timestamp}`,
                problemStatement: `# Essay: What is Computer Science?\n\nWrite a short essay (300-500 words) explaining what computer science is and why it matters in today's world.\n\n## Evaluation Criteria\n- Clarity of explanation\n- Use of examples\n- Writing quality`,
            },
            {
                type: 'modeling',
                title: 'UML Use Case Diagram: Library System',
                shortName: `UMLLib${timestamp}`,
                diagramType: 'UseCaseDiagram',
                problemStatement: `# UML Use Case Diagram: Library System\n\nDesign a use case diagram for a simple library management system.\n\n## Requirements\n1. Model actors: Librarian, Member, System\n2. Model use cases: Borrow Book, Return Book, Search Catalog, Register Member\n3. Show relationships between actors and use cases`,
            },
        ],
        lectures: [
            {
                title: 'What is Computer Science?',
                description: 'An introduction to the field of computer science and its applications.',
                textUnits: [
                    {
                        name: 'Overview of Computer Science',
                        content: `# Overview of Computer Science\n\nComputer science is the study of computation, information, and automation. It spans theoretical disciplines such as algorithms and complexity theory to practical areas like software engineering and artificial intelligence.\n\n## Subfields\n- **Theoretical CS**: Algorithms, complexity theory, formal languages\n- **Systems**: Operating systems, networks, databases\n- **AI & ML**: Machine learning, natural language processing\n- **Software Engineering**: Development practices, testing, maintenance`,
                    },
                ],
            },
            {
                title: 'Introduction to Python Programming',
                description: 'Learn the basics of Python: variables, control flow, functions, and data structures.',
                textUnits: [
                    {
                        name: 'Python Basics',
                        content: `# Python Basics\n\n## Variables and Types\n\`\`\`python\nname = "Alice"    # str\nage = 25          # int\nheight = 1.75     # float\nis_student = True # bool\n\`\`\`\n\n## Control Flow\n\`\`\`python\nif age >= 18:\n    print("Adult")\nelse:\n    print("Minor")\n\nfor i in range(5):\n    print(i)\n\`\`\`\n\n## Functions\n\`\`\`python\ndef greet(name="World"):\n    return f"Hello, {name}!"\n\`\`\``,
                    },
                ],
            },
        ],
    },
    {
        title: 'Software Engineering Fundamentals',
        shortName: 'SE2026',
        semester: 'WS2025',
        description: 'Covers software development processes, design patterns, testing strategies, and team collaboration using modern tools.',
        studentCount: 20,
        exercises: [
            {
                type: 'programming',
                title: 'Java Unit Testing with JUnit',
                shortName: `JUnit${timestamp}`,
                programmingLanguage: 'JAVA',
                projectType: 'PLAIN_GRADLE',
                packageName: 'de.tum.cit.se.testing',
                problemStatement: `# Java Unit Testing with JUnit\n\nWrite unit tests for a given Calculator class using JUnit 5.\n\n## Requirements\n1. Test all arithmetic operations: add, subtract, multiply, divide\n2. Test edge cases: division by zero, integer overflow\n3. Use \`@ParameterizedTest\` for at least one test\n4. Achieve at least 90% code coverage`,
            },
            {
                type: 'text',
                title: 'Report: Agile vs Waterfall',
                shortName: `ReportAgile${timestamp}`,
                problemStatement: `# Report: Agile vs Waterfall\n\nCompare and contrast the Agile and Waterfall software development methodologies.\n\n## Requirements\n1. Describe each methodology\n2. Discuss advantages and disadvantages\n3. Give examples of when each is appropriate\n4. State your recommendation with justification`,
            },
            {
                type: 'modeling',
                title: 'UML Class Diagram: Online Shop',
                shortName: `UMLShop${timestamp}`,
                diagramType: 'ClassDiagram',
                problemStatement: `# UML Class Diagram: Online Shop\n\nDesign a class diagram for an online shopping system.\n\n## Requirements\n1. Model classes: Customer, Product, Order, ShoppingCart, Payment\n2. Show attributes and methods for each class\n3. Model associations, including multiplicities\n4. Use inheritance where appropriate (e.g., different payment types)`,
            },
            {
                type: 'modeling',
                title: 'Activity Diagram: CI/CD Pipeline',
                shortName: `ActCI${timestamp}`,
                diagramType: 'ActivityDiagram',
                problemStatement: `# Activity Diagram: CI/CD Pipeline\n\nCreate an activity diagram showing a typical CI/CD pipeline.\n\n## Requirements\n1. Model stages: Build, Test, Static Analysis, Deploy\n2. Include decision points for pass/fail\n3. Show parallel activities where applicable\n4. Include notification steps`,
            },
        ],
        lectures: [
            {
                title: 'Software Development Processes',
                description: 'Overview of software development lifecycles and methodologies.',
                textUnits: [
                    {
                        name: 'Agile and Scrum',
                        content: `# Agile and Scrum\n\n## Agile Manifesto Principles\n- Individuals and interactions over processes and tools\n- Working software over comprehensive documentation\n- Customer collaboration over contract negotiation\n- Responding to change over following a plan\n\n## Scrum Framework\n- **Sprint**: Fixed-length iteration (2-4 weeks)\n- **Daily Standup**: 15-minute sync\n- **Sprint Review**: Demo completed work\n- **Sprint Retrospective**: Continuous improvement`,
                    },
                ],
            },
            {
                title: 'Design Patterns in Practice',
                description: 'Learn about common design patterns and how to apply them in real-world software.',
                textUnits: [
                    {
                        name: 'Creational Patterns',
                        content: `# Creational Design Patterns\n\n## Singleton\nEnsures a class has only one instance.\n\n## Factory Method\nDefines an interface for creating objects, letting subclasses decide which class to instantiate.\n\n## Builder\nSeparates construction of a complex object from its representation.\n\n## Abstract Factory\nProvides an interface for creating families of related objects.`,
                    },
                ],
            },
            {
                title: 'Testing Strategies',
                description: 'Unit testing, integration testing, and test-driven development.',
                textUnits: [
                    {
                        name: 'Testing Pyramid',
                        content: `# The Testing Pyramid\n\n## Unit Tests (Base)\n- Fast, isolated, many\n- Test individual methods/classes\n- Mock external dependencies\n\n## Integration Tests (Middle)\n- Test component interactions\n- Use real databases/services where possible\n- Slower but more realistic\n\n## E2E Tests (Top)\n- Test full user workflows\n- Slowest, most brittle\n- Use sparingly for critical paths`,
                    },
                ],
            },
        ],
    },
];

// ─── Main Setup ───────────────────────────────────────────────────────────────

async function main() {
    console.log('='.repeat(60));
    console.log('  Demo Data Setup — Master Thesis Presentation');
    console.log('  "Data Structures and Algorithms" (SS 2026)');
    console.log('='.repeat(60));
    console.log(`  Server:   ${config.serverUrl}`);
    console.log(`  Admin:    ${config.adminUser}`);
    console.log(`  Students: ${config.studentCount}`);
    console.log('='.repeat(60));
    console.log('');

    const client = new HttpClient(config.serverUrl);

    // ── Step 1: Authenticate ──────────────────────────────────────────────
    console.log('[1/10] Authenticating as admin...');
    await authenticate(client, config.adminUser, config.adminPassword);

    // ── Step 2: Create course ─────────────────────────────────────────────
    console.log('[2/10] Creating course...');
    let course;
    try {
        course = await createCourse(client);
        console.log(`  Created course: "${course.title}" (ID: ${course.id})`);
    } catch (error) {
        if (error.response?.status === 400) {
            console.log(`  Course "${COURSE_TITLE}" may already exist. Searching...`);
            // Try to find the existing course
            const coursesResp = await client.get('/api/core/courses');
            const existing = coursesResp.data.find(c => c.shortName === COURSE_SHORT_NAME);
            if (existing) {
                course = existing;
                console.log(`  Found existing course: "${course.title}" (ID: ${course.id})`);
            } else {
                throw error;
            }
        } else {
            throw error;
        }
    }
    const courseId = course.id;

    // ── Step 3: Create demo characters ────────────────────────────────────
    console.log('[3/10] Creating demo characters...');
    const createdDemoUsers = {};
    for (const [key, userData] of Object.entries(DEMO_USERS)) {
        const user = await createUser(client, userData);
        createdDemoUsers[key] = user;
        const status = user.isNew ? 'created' : 'exists';
        console.log(`  ${status}: ${userData.login} (${userData.firstName} ${userData.lastName}) — ${userData.description}`);

        // Add to course
        if (userData.role === 'instructor') {
            await addUserToCourse(client, courseId, 'instructors', userData.login);
        } else {
            await addUserToCourse(client, courseId, 'students', userData.login);
        }
    }

    // Grant admin role to Sofia and Tom
    await grantAdminRole(client, 'prof_sofia');
    await grantAdminRole(client, 'tom');

    // ── Step 4: Create generic students ───────────────────────────────────
    console.log(`[4/10] Creating ${config.studentCount} students...`);
    const students = [];
    // Use realistic German first names for believability
    const firstNames = [
        'Emma', 'Mia', 'Hannah', 'Sophia', 'Lena', 'Marie', 'Leonie', 'Amelie',
        'Anna', 'Luisa', 'Clara', 'Lea', 'Emilia', 'Johanna', 'Laura', 'Julia',
        'Ben', 'Paul', 'Jonas', 'Elias', 'Leon', 'Finn', 'Noah', 'Luis',
        'Felix', 'Maximilian', 'Moritz', 'David', 'Tim', 'Niklas', 'Jan', 'Erik',
        'Sarah', 'Lisa', 'Katharina', 'Theresa', 'Helena', 'Marlene', 'Charlotte', 'Ella',
        'Tobias', 'Sebastian', 'Alexander', 'Fabian', 'Philipp', 'Patrick', 'Michael', 'Daniel',
        'Nina', 'Carla',
    ];
    const lastNames = [
        'Mueller', 'Schmidt', 'Schneider', 'Fischer', 'Weber', 'Wagner', 'Becker', 'Hoffmann',
        'Koch', 'Richter', 'Klein', 'Wolf', 'Neumann', 'Schwarz', 'Braun', 'Zimmermann',
        'Krueger', 'Hartmann', 'Lange', 'Werner', 'Peters', 'Schmitt', 'Meyer', 'Schulz',
        'Frank', 'Berger', 'Kaiser', 'Vogel', 'Friedrich', 'Keller', 'Fuchs', 'Scholz',
        'Roth', 'Huber', 'Seidel', 'Lang', 'Kraus', 'Baumann', 'Winter', 'Sommer',
        'Schubert', 'Kraft', 'Vogt', 'Stein', 'Beck', 'Haas', 'Ludwig', 'Brandt',
        'Lorenz', 'Jung',
    ];

    let newCount = 0;
    let existingCount = 0;
    for (let i = 1; i <= config.studentCount; i++) {
        const firstName = firstNames[(i - 1) % firstNames.length];
        const lastName = lastNames[(i - 1) % lastNames.length];
        const user = await createUser(client, {
            login: `dsa_student_${i}`,
            firstName,
            lastName,
            email: `dsa_student_${i}@tum.test`,
            password: STUDENT_PASSWORD,
        });
        students.push(user);
        user.isNew ? newCount++ : existingCount++;
        await addUserToCourse(client, courseId, 'students', user.login);
    }
    console.log(`  ${newCount} new, ${existingCount} existing students added to course`);

    // Also add 2 tutors
    console.log('  Creating 2 tutors...');
    const tutors = [];
    for (let i = 1; i <= 2; i++) {
        const user = await createUser(client, {
            login: `dsa_tutor_${i}`,
            firstName: `Tutor`,
            lastName: `DSA${i}`,
            email: `dsa_tutor_${i}@tum.test`,
            password: STUDENT_PASSWORD,
        });
        tutors.push(user);
        await addUserToCourse(client, courseId, 'tutors', user.login);
    }
    console.log(`  Created ${tutors.length} tutors`);

    // ── Step 5: Create programming exercises ──────────────────────────────
    console.log('[5/10] Creating programming exercises...');
    const programmingExercises = [];
    for (const exConfig of PROGRAMMING_EXERCISES) {
        const exercise = {
            type: 'programming',
            title: exConfig.title,
            shortName: exConfig.shortName,
            course: { id: courseId },
            programmingLanguage: exConfig.programmingLanguage,
            projectType: exConfig.projectType,
            allowOnlineEditor: true,
            allowOfflineIde: true,
            maxPoints: 100,
            assessmentType: 'AUTOMATIC',
            packageName: exConfig.packageName,
            staticCodeAnalysisEnabled: exConfig.staticCodeAnalysisEnabled,
            sequentialTestRuns: false,
            releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(), // 1 week ago
            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),     // 2 weeks from now
            problemStatement: exConfig.problemStatement,
            buildConfig: {
                buildScript: `#!/usr/bin/env bash\nset -e\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\nmain () {\n  gradle\n}\nmain "\${@}"`,
                checkoutSolutionRepository: false,
            },
        };

        try {
            const response = await client.post('/api/programming/programming-exercises/setup', exercise);
            programmingExercises.push(response.data);
            console.log(`  Created: ${exConfig.title}`);
        } catch (error) {
            console.log(`  Failed to create "${exConfig.title}": ${error.response?.data?.message || error.message}`);
        }
    }

    // ── Step 6: Create other exercises ────────────────────────────────────
    console.log('[6/10] Creating modeling, text, and quiz exercises...');

    // Modeling exercises
    const modelingExercises = [];
    for (const exConfig of MODELING_EXERCISES) {
        const exercise = {
            type: 'modeling',
            title: exConfig.title,
            shortName: exConfig.shortName,
            course: { id: courseId },
            diagramType: exConfig.diagramType,
            difficulty: exConfig.difficulty,
            maxPoints: 100,
            assessmentType: 'MANUAL',
            releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
            exampleSolutionModel: JSON.stringify({
                version: '2.0.0',
                type: exConfig.diagramType,
                size: { width: 800, height: 600 },
                interactive: { elements: [], relationships: [] },
                elements: [],
                relationships: [],
                assessments: [],
            }),
            problemStatement: exConfig.problemStatement,
        };

        try {
            const response = await client.post('/api/modeling/modeling-exercises', exercise);
            modelingExercises.push(response.data);
            console.log(`  Created: ${exConfig.title}`);
        } catch (error) {
            console.log(`  Failed to create "${exConfig.title}": ${error.response?.data?.message || error.message}`);
        }
    }

    // Text exercises
    const textExercises = [];
    for (const exConfig of TEXT_EXERCISES) {
        const exercise = {
            type: 'text',
            title: exConfig.title,
            shortName: exConfig.shortName,
            course: { id: courseId },
            difficulty: exConfig.difficulty,
            maxPoints: 100,
            assessmentType: 'MANUAL',
            releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
            problemStatement: exConfig.problemStatement,
        };

        try {
            const response = await client.post('/api/text/text-exercises', exercise);
            textExercises.push(response.data);
            console.log(`  Created: ${exConfig.title}`);
        } catch (error) {
            console.log(`  Failed to create "${exConfig.title}": ${error.response?.data?.message || error.message}`);
        }
    }

    // Quiz exercises
    const quizExercises = [];
    for (const quizConfig of QUIZ_EXERCISES) {
        const quizQuestions = quizConfig.questions.map(q => createQuizQuestion(q));

        const exercise = {
            type: 'quiz',
            title: quizConfig.title,
            shortName: quizConfig.shortName,
            course: { id: courseId },
            duration: quizConfig.duration,
            quizQuestions,
            isVisibleBeforeStart: false,
            isOpenForPractice: false,
            isPlannedToStart: false,
            randomizeQuestionOrder: false,
            releaseDate: new Date().toISOString(),
            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
            quizMode: 'SYNCHRONIZED',
            mode: 'INDIVIDUAL',
            includedInOverallScore: 'INCLUDED_COMPLETELY',
        };

        try {
            const { body, contentType } = createMultipartFormData({ exercise });
            const response = await client.post(`/api/quiz/courses/${courseId}/quiz-exercises`, body, {
                headers: { 'Content-Type': contentType },
                contentType: 'multipart',
            });
            quizExercises.push(response.data);
            console.log(`  Created: ${quizConfig.title}`);

            // Start the quiz
            try {
                await client.put(`/api/quiz/quiz-exercises/${response.data.id}/start-now`);
            } catch { /* quiz may not be startable yet */ }
        } catch (error) {
            console.log(`  Failed to create "${quizConfig.title}": ${error.response?.data?.message || error.message}`);
        }
    }

    // ── Step 7: Create lectures ───────────────────────────────────────────
    console.log('[7/10] Creating lectures...');
    const createdLectures = [];
    for (const lectConfig of LECTURES) {
        try {
            // Create lecture
            const lectureResp = await client.post('/api/lecture/lectures', {
                title: lectConfig.title,
                description: lectConfig.description,
                course: { id: courseId },
                visibleDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            });
            const lecture = lectureResp.data;
            createdLectures.push(lecture);
            console.log(`  Created lecture: ${lectConfig.title}`);

            // Add text units
            for (const unit of lectConfig.textUnits) {
                try {
                    await client.post(`/api/lecture/lectures/${lecture.id}/text-units`, {
                        type: 'text',
                        name: unit.name,
                        content: unit.content,
                        lecture: { id: lecture.id },
                        releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                    });
                    console.log(`    Added text unit: ${unit.name}`);
                } catch (error) {
                    console.log(`    Failed to add text unit "${unit.name}": ${error.message}`);
                }
            }
        } catch (error) {
            console.log(`  Failed to create lecture "${lectConfig.title}": ${error.message}`);
        }
    }

    // Link some exercises to lectures as exercise units
    if (createdLectures.length >= 2 && programmingExercises.length >= 1) {
        try {
            // Link BST exercise to Trees lecture
            const treesLecture = createdLectures[1]; // "Trees and Binary Search Trees"
            const bstExercise = programmingExercises[0]; // "Binary Search Trees"
            await client.post(`/api/lecture/lectures/${treesLecture.id}/exercise-units`, {
                type: 'exercise',
                exercise: { id: bstExercise.id, type: bstExercise.type },
                lecture: { id: treesLecture.id },
            });
            console.log(`    Linked exercise "${bstExercise.title}" to lecture "${treesLecture.title}"`);
        } catch (error) {
            console.log(`    Could not link exercise to lecture: ${error.message}`);
        }
    }

    if (createdLectures.length >= 3 && programmingExercises.length >= 4) {
        try {
            // Link Sorting exercise to Sorting lecture
            const sortLecture = createdLectures[2]; // "Sorting Algorithms"
            const sortExercise = programmingExercises[3]; // "Sorting Algorithms"
            await client.post(`/api/lecture/lectures/${sortLecture.id}/exercise-units`, {
                type: 'exercise',
                exercise: { id: sortExercise.id, type: sortExercise.type },
                lecture: { id: sortLecture.id },
            });
            console.log(`    Linked exercise "${sortExercise.title}" to lecture "${sortLecture.title}"`);
        } catch (error) {
            console.log(`    Could not link exercise to lecture: ${error.message}`);
        }
    }

    // ── Step 8: Create communication data ─────────────────────────────────
    if (!config.skipParticipations) {
        console.log('[8/10] Creating communication data...');
        try {
            // Get course channels
            const channelsResp = await client.get(`/api/communication/courses/${courseId}/channels/overview`);
            const channels = channelsResp.data || [];

            // Find a suitable channel (general or course-wide)
            let channel = channels.find(c => {
                const name = c.name || '';
                const isAutoGenerated = name.startsWith('exam-') || name.startsWith('exercise-') || name.startsWith('lecture-');
                return (c.isCourseWide || c.isPublic) && !c.isAnnouncementChannel && !isAutoGenerated;
            });

            if (!channel) {
                // Create a general channel
                try {
                    const chResp = await client.post(`/api/communication/courses/${courseId}/channels`, {
                        name: 'general',
                        description: 'General discussion channel for all course participants',
                        isPublic: true,
                        isAnnouncementChannel: false,
                        isCourseWide: true,
                    });
                    channel = chResp.data;
                    console.log(`  Created channel: ${channel.name}`);
                } catch {
                    console.log('  Could not create channel, skipping communication data');
                }
            }

            if (channel) {
                console.log(`  Using channel: ${channel.name}`);

                // Post messages as the demo characters
                const authorMap = {
                    sofia: DEMO_USERS.sofia,
                    luca: DEMO_USERS.luca,
                    tom: DEMO_USERS.tom,
                };

                const createdPosts = [];
                for (const postConfig of DSA_POSTS) {
                    const authorData = authorMap[postConfig.author];
                    const authorClient = new HttpClient(config.serverUrl);
                    try {
                        await authenticate(authorClient, authorData.login, authorData.password, true);
                        const postResp = await authorClient.post(`/api/communication/courses/${courseId}/messages`, {
                            content: postConfig.content,
                            title: postConfig.title || null,
                            hasForwardedMessages: false,
                            conversation: { id: channel.id },
                        });
                        createdPosts.push(postResp.data);
                        console.log(`    ${authorData.login}: Posted "${postConfig.title}"`);
                    } catch (error) {
                        console.log(`    Could not post as ${authorData.login}: ${error.response?.data?.message || error.message}`);
                    }
                }

                // Add answers from various students
                if (createdPosts.length > 0) {
                    const answerAuthors = [
                        { login: 'luca', password: STUDENT_PASSWORD },
                        ...students.slice(0, 5).map(s => ({ login: s.login, password: STUDENT_PASSWORD })),
                        { login: 'tom', password: STUDENT_PASSWORD },
                    ];

                    for (let i = 0; i < DSA_ANSWERS.length && i < answerAuthors.length; i++) {
                        const author = answerAuthors[i % answerAuthors.length];
                        const post = createdPosts[i % createdPosts.length];
                        const answerClient = new HttpClient(config.serverUrl);
                        try {
                            await authenticate(answerClient, author.login, author.password, true);
                            await answerClient.post(`/api/communication/courses/${courseId}/answer-messages`, {
                                content: DSA_ANSWERS[i],
                                post: { id: post.id },
                            });
                            console.log(`    ${author.login}: Replied to post`);
                        } catch { /* skip */ }
                    }
                }

                // Add some reactions
                const emojis = ['thumbsup', 'heart', 'rocket', 'fire', 'tada'];
                for (let i = 0; i < Math.min(10, students.length); i++) {
                    const student = students[i];
                    const post = createdPosts[i % createdPosts.length];
                    if (!post) continue;
                    const studentClient = new HttpClient(config.serverUrl);
                    try {
                        await authenticate(studentClient, student.login, STUDENT_PASSWORD, true);
                        await studentClient.post(`/api/communication/courses/${courseId}/postings/reactions`, {
                            emojiId: emojis[i % emojis.length],
                            relatedPostId: post.id,
                        });
                    } catch { /* skip */ }
                }
                console.log(`  Created ${createdPosts.length} posts with answers and reactions`);
            }
        } catch (error) {
            console.log(`  Communication data setup failed: ${error.message}`);
        }
    } else {
        console.log('[8/10] Skipping communication data (as requested)');
    }

    // ── Step 9: Create additional courses (Sofia as instructor) ──────────
    console.log('[9/10] Creating additional courses for Prof. Sofia...');
    const additionalCourseIds = [];
    for (const addCourse of ADDITIONAL_COURSES) {
        try {
            const now = new Date();
            const startDate = new Date(now.getTime() - 60 * 24 * 60 * 60 * 1000); // 60 days ago
            const endDate = new Date(now.getTime() + 60 * 24 * 60 * 60 * 1000);   // 60 days from now

            const courseData = {
                title: addCourse.title,
                shortName: addCourse.shortName,
                startDate: startDate.toISOString(),
                endDate: endDate.toISOString(),
                complaintsEnabled: true,
                requestMoreFeedbackEnabled: true,
                maxComplaints: 3,
                maxTeamComplaints: 3,
                maxComplaintTimeDays: 7,
                maxComplaintTextLimit: 2000,
                maxComplaintResponseTextLimit: 2000,
                maxRequestMoreFeedbackTimeDays: 7,
                courseInformationSharingConfiguration: 'COMMUNICATION_AND_MESSAGING',
                enrollmentEnabled: true,
                accuracyOfScores: 1,
                onlineCourse: false,
                timeZone: 'Europe/Berlin',
                semester: addCourse.semester,
                description: addCourse.description,
            };

            let createdAddCourse;
            try {
                const { body, contentType } = createMultipartFormData({ course: courseData });
                const resp = await client.post('/api/core/admin/courses', body, {
                    headers: { 'Content-Type': contentType },
                    contentType: 'multipart',
                });
                createdAddCourse = resp.data;
            } catch (err) {
                if (err.response?.status === 400) {
                    console.log(`  Course "${addCourse.title}" may already exist, searching...`);
                    const coursesResp = await client.get('/api/core/courses');
                    createdAddCourse = coursesResp.data.find(c => c.shortName === addCourse.shortName);
                    if (!createdAddCourse) throw err;
                } else {
                    throw err;
                }
            }
            const addCourseId = createdAddCourse.id;
            additionalCourseIds.push({ id: addCourseId, title: addCourse.title });
            console.log(`  Created course: "${addCourse.title}" (ID: ${addCourseId})`);

            // Add Sofia as instructor, Tom as instructor, Luca as student
            await addUserToCourse(client, addCourseId, 'instructors', 'prof_sofia');
            await addUserToCourse(client, addCourseId, 'instructors', 'tom');
            await addUserToCourse(client, addCourseId, 'students', 'luca');

            // Add some students to make it look populated
            for (let i = 1; i <= Math.min(addCourse.studentCount, students.length); i++) {
                await addUserToCourse(client, addCourseId, 'students', students[i - 1].login);
            }
            console.log(`  Added ${Math.min(addCourse.studentCount, students.length)} students`);

            // Create exercises
            for (const ex of addCourse.exercises) {
                try {
                    if (ex.type === 'programming') {
                        const progEx = {
                            type: 'programming',
                            title: ex.title,
                            shortName: ex.shortName,
                            course: { id: addCourseId },
                            programmingLanguage: ex.programmingLanguage,
                            projectType: ex.projectType,
                            allowOnlineEditor: true,
                            allowOfflineIde: true,
                            maxPoints: 100,
                            assessmentType: 'AUTOMATIC',
                            packageName: ex.packageName,
                            staticCodeAnalysisEnabled: false,
                            sequentialTestRuns: false,
                            releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
                            problemStatement: ex.problemStatement,
                            buildConfig: {
                                buildScript: ex.programmingLanguage === 'PYTHON'
                                    ? `#!/usr/bin/env bash\nset -e\npython3 -m pytest --junitxml=test-reports/results.xml`
                                    : `#!/usr/bin/env bash\nset -e\ngradle () {\n  echo '⚙️ executing gradle'\n  chmod +x ./gradlew\n  ./gradlew clean test\n}\nmain () {\n  gradle\n}\nmain "\${@}"`,
                                checkoutSolutionRepository: false,
                            },
                        };
                        await client.post('/api/programming/programming-exercises/setup', progEx);
                        console.log(`    Created programming exercise: ${ex.title}`);
                    } else if (ex.type === 'text') {
                        await client.post('/api/text/text-exercises', {
                            type: 'text',
                            title: ex.title,
                            shortName: ex.shortName,
                            course: { id: addCourseId },
                            difficulty: 'EASY',
                            maxPoints: 100,
                            assessmentType: 'MANUAL',
                            releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
                            problemStatement: ex.problemStatement,
                        });
                        console.log(`    Created text exercise: ${ex.title}`);
                    } else if (ex.type === 'modeling') {
                        await client.post('/api/modeling/modeling-exercises', {
                            type: 'modeling',
                            title: ex.title,
                            shortName: ex.shortName,
                            course: { id: addCourseId },
                            diagramType: ex.diagramType,
                            difficulty: 'MEDIUM',
                            maxPoints: 100,
                            assessmentType: 'MANUAL',
                            releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                            dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
                            exampleSolutionModel: JSON.stringify({
                                version: '2.0.0', type: ex.diagramType,
                                size: { width: 800, height: 600 },
                                interactive: { elements: [], relationships: [] },
                                elements: [], relationships: [], assessments: [],
                            }),
                            problemStatement: ex.problemStatement,
                        });
                        console.log(`    Created modeling exercise: ${ex.title}`);
                    }
                } catch (error) {
                    console.log(`    Failed to create "${ex.title}": ${error.response?.data?.message || error.message}`);
                }
            }

            // Create lectures
            for (const lect of addCourse.lectures) {
                try {
                    const lectResp = await client.post('/api/lecture/lectures', {
                        title: lect.title,
                        description: lect.description,
                        course: { id: addCourseId },
                        visibleDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                    });
                    console.log(`    Created lecture: ${lect.title}`);

                    for (const unit of lect.textUnits) {
                        try {
                            await client.post(`/api/lecture/lectures/${lectResp.data.id}/text-units`, {
                                type: 'text',
                                name: unit.name,
                                content: unit.content,
                                lecture: { id: lectResp.data.id },
                                releaseDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
                            });
                        } catch { /* skip */ }
                    }
                } catch (error) {
                    console.log(`    Failed to create lecture "${lect.title}": ${error.message}`);
                }
            }
        } catch (error) {
            console.log(`  Failed to create additional course "${addCourse.title}": ${error.message}`);
        }
    }

    // ── Step 10: Summary ──────────────────────────────────────────────────
    console.log('[10/10] Setup complete!');
    console.log('');
    console.log('='.repeat(60));
    console.log('  DEMO DATA SETUP COMPLETE');
    console.log('='.repeat(60));
    console.log('');
    console.log(`  Course: "${COURSE_TITLE}" (ID: ${courseId})`);
    console.log(`  Short name: ${COURSE_SHORT_NAME}`);
    console.log('');
    console.log('  Demo Characters:');
    console.log(`    Prof. Sofia  → login: prof_sofia  / password: ${STUDENT_PASSWORD}  (Admin + Instructor)`);
    console.log(`    Luca         → login: luca        / password: ${STUDENT_PASSWORD}  (Student)`);
    console.log(`    Tom          → login: tom         / password: ${STUDENT_PASSWORD}  (Instructor + Admin)`);
    console.log('');
    console.log(`  Students: ${students.length} generic students (dsa_student_1 .. dsa_student_${config.studentCount})`);
    console.log(`  Tutors:   ${tutors.length} tutors (dsa_tutor_1, dsa_tutor_2)`);
    console.log('');
    console.log('  Exercises:');
    console.log(`    Programming:  ${programmingExercises.length} (incl. "Binary Search Trees")`);
    console.log(`    Modeling:     ${modelingExercises.length}`);
    console.log(`    Text:         ${textExercises.length}`);
    console.log(`    Quiz:         ${quizExercises.length}`);
    console.log('');
    console.log(`  Lectures: ${createdLectures.length}`);
    for (const l of createdLectures) {
        console.log(`    - ${l.title}`);
    }
    console.log('');
    console.log('  Additional Courses (Prof. Sofia is instructor):');
    for (const ac of additionalCourseIds) {
        console.log(`    - "${ac.title}" (ID: ${ac.id})`);
    }
    console.log('');
    console.log('  Demo Flow:');
    console.log('    1. Login as prof_sofia → register passkey → logout → login with passkey');
    console.log('    2. Show locked admin action (login with password only)');
    console.log('    3. Cmd+K → type "binary search trees" → results from exercises + lectures');
    console.log('');
    console.log('='.repeat(60));
}

main().catch(error => {
    console.error('');
    console.error('='.repeat(60));
    console.error('  DEMO SETUP FAILED');
    console.error('='.repeat(60));
    console.error(error.message);
    if (error.response) {
        console.error('Response status:', error.response.status);
        console.error('Response body:', JSON.stringify(error.response.data, null, 2));
    }
    process.exit(1);
});
