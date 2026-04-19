package com.yourplugin.dynamodb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class CreateTableDialog(
    project: Project,
    private val client: DynamoDbAsyncClient,
    private val onCreated: (String) -> Unit,
) : DialogWrapper(project) {

    // ── Base table fields ─────────────────────────────────────────────────────
    private val tableNameField   = JTextField(25)
    private val pkNameField      = JTextField("pk", 15)
    private val pkTypeBox        = JComboBox(arrayOf("S", "N", "B"))
    private val skEnabledBox     = JCheckBox("Add Sort Key")
    private val skNameField      = JTextField("sk", 15).apply { isEnabled = false }
    private val skTypeBox        = JComboBox(arrayOf("S", "N", "B")).apply { isEnabled = false }

    // ── Billing ───────────────────────────────────────────────────────────────
    private val billingGroup     = ButtonGroup()
    private val onDemandRadio    = JRadioButton("On-Demand", true)
    private val provisionedRadio = JRadioButton("Provisioned")
    private val rcuField         = JTextField("5", 6).apply { isEnabled = false }
    private val wcuField         = JTextField("5", 6).apply { isEnabled = false }

    // ── GSIs ──────────────────────────────────────────────────────────────────
    private val gsiModel = object : DefaultTableModel(
        arrayOf("Index Name", "PK", "PK Type", "SK", "SK Type", "Projection"), 0
    ) { override fun isCellEditable(r: Int, c: Int) = true }
    private val gsiTable = JBTable(gsiModel).apply { rowHeight = 24 }

    init {
        title = "Create DynamoDB Table"
        setSize(700, 560)
        billingGroup.add(onDemandRadio)
        billingGroup.add(provisionedRadio)

        skEnabledBox.addActionListener {
            skNameField.isEnabled = skEnabledBox.isSelected
            skTypeBox.isEnabled   = skEnabledBox.isSelected
        }
        provisionedRadio.addActionListener {
            rcuField.isEnabled = true; wcuField.isEnabled = true
        }
        onDemandRadio.addActionListener {
            rcuField.isEnabled = false; wcuField.isEnabled = false
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 6, 4, 6); fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
        }

        fun label(text: String, row: Int, col: Int = 0) {
            gbc.gridx = col; gbc.gridy = row; gbc.weightx = 0.0; gbc.gridwidth = 1
            panel.add(JLabel(text), gbc)
        }
        fun field(comp: JComponent, row: Int, col: Int = 1, width: Int = 1) {
            gbc.gridx = col; gbc.gridy = row; gbc.weightx = 1.0; gbc.gridwidth = width
            panel.add(comp, gbc)
        }

        // Table name
        label("Table name:", 0); field(tableNameField, 0)

        // PK
        label("Partition key:", 1); field(pkNameField, 1)
        label("Type:", 2, 0); field(pkTypeBox, 2)

        // SK
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3; gbc.weightx = 0.0
        panel.add(skEnabledBox, gbc)
        label("Sort key:", 4); field(skNameField, 4)
        label("Type:", 5, 0); field(skTypeBox, 5)

        // Billing
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3
        panel.add(JSeparator(), gbc)
        gbc.gridy = 7; gbc.gridwidth = 1
        panel.add(JLabel("Billing mode:"), gbc)
        val billingRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(onDemandRadio); add(provisionedRadio)
            add(JLabel("  RCU:")); add(rcuField)
            add(JLabel("WCU:")); add(wcuField)
        }
        gbc.gridx = 1; gbc.gridy = 7; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(billingRow, gbc)

        // GSI section
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 3; gbc.weightx = 0.0
        panel.add(JSeparator(), gbc)
        gbc.gridy = 9
        panel.add(JLabel("Global Secondary Indexes (optional):"), gbc)

        val gsiButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JButton("+ Add GSI").apply {
                addActionListener {
                    gsiModel.addRow(arrayOf("gsi-name", "gsiPk", "S", "", "S", "ALL"))
                }
            })
            add(JButton("- Remove").apply {
                addActionListener {
                    val row = gsiTable.selectedRow
                    if (row >= 0) gsiModel.removeRow(row)
                }
            })
        }
        gbc.gridy = 10
        panel.add(gsiButtons, gbc)

        gbc.gridy = 11; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        panel.add(JBScrollPane(gsiTable).apply { preferredSize = Dimension(0, 130) }, gbc)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (tableNameField.text.isBlank()) return ValidationInfo("Table name is required", tableNameField)
        if (pkNameField.text.isBlank())    return ValidationInfo("Partition key name is required", pkNameField)
        if (skEnabledBox.isSelected && skNameField.text.isBlank())
            return ValidationInfo("Sort key name is required", skNameField)
        return null
    }

    override fun doOKAction() {
        val tableName = tableNameField.text.trim()
        val pkName    = pkNameField.text.trim()
        val pkType    = ScalarAttributeType.fromValue(pkTypeBox.selectedItem as String)

        val attrDefs  = mutableListOf(AttributeDefinition.builder().attributeName(pkName).attributeType(pkType).build())
        val keySchema = mutableListOf(KeySchemaElement.builder().attributeName(pkName).keyType(KeyType.HASH).build())

        if (skEnabledBox.isSelected) {
            val skName = skNameField.text.trim()
            val skType = ScalarAttributeType.fromValue(skTypeBox.selectedItem as String)
            attrDefs.add(AttributeDefinition.builder().attributeName(skName).attributeType(skType).build())
            keySchema.add(KeySchemaElement.builder().attributeName(skName).keyType(KeyType.RANGE).build())
        }

        val gsis = mutableListOf<GlobalSecondaryIndex>()
        for (row in 0 until gsiModel.rowCount) {
            val gsiName   = gsiModel.getValueAt(row, 0).toString().trim()
            val gsiPk     = gsiModel.getValueAt(row, 1).toString().trim()
            val gsiPkType = ScalarAttributeType.fromValue(gsiModel.getValueAt(row, 2).toString())
            val gsiSk     = gsiModel.getValueAt(row, 3).toString().trim()
            val gsiSkType = ScalarAttributeType.fromValue(gsiModel.getValueAt(row, 4).toString())
            val projection= gsiModel.getValueAt(row, 5).toString()

            if (gsiName.isBlank() || gsiPk.isBlank()) continue

            attrDefs.add(AttributeDefinition.builder().attributeName(gsiPk).attributeType(gsiPkType).build())
            val gsiKeys = mutableListOf(KeySchemaElement.builder().attributeName(gsiPk).keyType(KeyType.HASH).build())
            if (gsiSk.isNotBlank()) {
                attrDefs.add(AttributeDefinition.builder().attributeName(gsiSk).attributeType(gsiSkType).build())
                gsiKeys.add(KeySchemaElement.builder().attributeName(gsiSk).keyType(KeyType.RANGE).build())
            }
            val proj = Projection.builder().projectionType(
                runCatching { ProjectionType.fromValue(projection) }.getOrDefault(ProjectionType.ALL)
            ).build()

            gsis.add(GlobalSecondaryIndex.builder()
                .indexName(gsiName).keySchema(gsiKeys).projection(proj)
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build())
        }

        val req = CreateTableRequest.builder()
            .tableName(tableName)
            .attributeDefinitions(attrDefs.distinctBy { it.attributeName() })
            .keySchema(keySchema)
            .apply {
                if (onDemandRadio.isSelected) {
                    billingMode(BillingMode.PAY_PER_REQUEST)
                } else {
                    billingMode(BillingMode.PROVISIONED)
                    provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(rcuField.text.toLongOrNull() ?: 5L)
                        .writeCapacityUnits(wcuField.text.toLongOrNull() ?: 5L)
                        .build())
                }
                if (gsis.isNotEmpty()) globalSecondaryIndexes(gsis)
            }
            .build()

        client.createTable(req)
            .thenAccept { onCreated(tableName) }
            .exceptionally { ex ->
                SwingUtilities.invokeLater {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        "Failed to create table: ${ex.cause?.message ?: ex.message}", "Error"
                    )
                }
                null
            }

        super.doOKAction()
    }
}
