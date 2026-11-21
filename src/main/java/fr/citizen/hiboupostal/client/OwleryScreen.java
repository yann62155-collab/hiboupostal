package fr.citizen.hiboupostal.client;

import fr.citizen.hiboupostal.item.LetterContent;
import fr.citizen.hiboupostal.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * La Volière (/hibou) : carnet de contacts à gauche, rédaction à droite,
 * avec deux onglets : Lettre et Beuglante (cri écrit + voix enregistrée au micro).
 */
public class OwleryScreen extends Screen {
    private static final int W = 320;
    private static final int H = 212;
    private static final int PAGE_SIZE = 7;
    private static final int CHUNK = 24000;

    private enum Mode { LETTER, BEUGLANTE }

    private final int myNumber;
    private int blankLetters;
    private int beuglantes;
    private final int maxTitle;
    private final int maxBody;
    private final int maxSeconds;
    private final List<Payloads.DirectoryEntry> contacts;

    private Mode mode = Mode.LETTER;
    private int left;
    private int top;
    private int page = 0;

    private EditBox recipientBox;
    private Button contactButton;
    private EditBox titleBox;
    private MultiLineEditBox bodyBox;
    private EditBox cryBox;
    private Button recButton;
    private Button stopButton;
    private Button playButton;
    private Button clearButton;
    private Button micButton;
    private boolean wasRecording = false;
    private Button sendButton;

    private String recipientText;
    private String titleText = "";
    private String bodyText = "";
    private String cryText = "";

    private Component status = Component.empty();
    private int statusColor = Parchment.INK_FAINT;
    private boolean waiting = false;

    public OwleryScreen(Payloads.OpenOwlery payload) {
        super(Component.literal("Volière"));
        this.myNumber = payload.myNumber();
        this.blankLetters = payload.blankLetters();
        this.beuglantes = payload.beuglantes();
        this.maxTitle = payload.maxTitle();
        this.maxBody = payload.maxBody();
        this.maxSeconds = payload.maxBeuglanteSeconds();
        this.contacts = new ArrayList<>(payload.contacts());
        this.recipientText = payload.prefill();
    }

    @Override
    protected void init() {
        // conserve le texte saisi lors d'un redimensionnement / changement de page / d'onglet
        if (recipientBox != null) recipientText = recipientBox.getValue();
        if (titleBox != null) titleText = titleBox.getValue();
        if (bodyBox != null) bodyText = bodyBox.getValue();
        if (cryBox != null) cryText = cryBox.getValue();
        titleBox = null;
        bodyBox = null;
        cryBox = null;
        recButton = stopButton = playButton = clearButton = micButton = null;

        left = (width - W) / 2;
        top = (height - H) / 2;

        // ----- Onglets -----
        Button letterTab = addRenderableWidget(Button.builder(Component.literal("✉ Lettre"), b -> switchMode(Mode.LETTER))
                .bounds(left + W - 124, top + 5, 56, 14).build());
        Button beuglanteTab = addRenderableWidget(Button.builder(
                        Component.literal("‼ Beuglante").withStyle(ChatFormatting.RED), b -> switchMode(Mode.BEUGLANTE))
                .bounds(left + W - 66, top + 5, 58, 14)
                .tooltip(Tooltip.create(Component.literal("Une lettre qui hurle son message à la face du destinataire, et de tous ceux qui l'entourent !")))
                .build());
        letterTab.active = mode != Mode.LETTER;
        beuglanteTab.active = mode != Mode.BEUGLANTE;

        // ----- Carnet de contacts (colonne gauche) -----
        int pages = Math.max(1, (contacts.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < contacts.size(); i++) {
            Payloads.DirectoryEntry e = contacts.get(start + i);
            String num = LetterContent.formatNumber(e.number());
            String name = font.plainSubstrByWidth(e.name(), 58);
            Component label = Component.literal(e.online() ? "● " : "○ ")
                    .withStyle(e.online() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.literal(num + " ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE));
            addRenderableWidget(Button.builder(label, b -> recipientBox.setValue(String.valueOf(e.number())))
                    .bounds(left + 8, top + 46 + i * 16, 102, 14)
                    .tooltip(Tooltip.create(Component.literal(e.name() + " — hibou n°" + num
                            + (e.online() ? "\nPrésent en ces terres" : "\nAbsent du royaume"))))
                    .build());
        }
        Button prev = addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page--; rebuildWidgets(); })
                .bounds(left + 8, top + 168, 16, 14).build());
        Button next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page++; rebuildWidgets(); })
                .bounds(left + 94, top + 168, 16, 14).build());
        prev.active = page > 0;
        next.active = page < pages - 1;

        addRenderableWidget(Button.builder(Component.literal("Envoyer un colis"),
                        b -> PacketDistributor.sendToServer(new Payloads.RequestParcel(recipientBox.getValue().trim())))
                .bounds(left + 8, top + 186, 102, 18)
                .tooltip(Tooltip.create(Component.literal("Expédier un carton de 5 emplacements à ce destinataire.")))
                .build());

        // ----- Destinataire (commun aux deux onglets) -----
        int rx = left + 118;
        int rw = W - 118 - 10;

        recipientBox = new EditBox(font, rx, top + 44, rw - 52, 16, Component.literal("Destinataire"));
        recipientBox.setMaxLength(32);
        recipientBox.setHint(Component.literal("n° de hibou ou contact").withStyle(ChatFormatting.DARK_GRAY));
        recipientBox.setValue(recipientText);
        recipientBox.setResponder(s -> updateContactButton());
        addRenderableWidget(recipientBox);

        contactButton = addRenderableWidget(Button.builder(Component.literal("★ Ajouter"), b -> toggleContact())
                .bounds(rx + rw - 48, top + 43, 48, 18).build());
        updateContactButton();

        if (mode == Mode.LETTER) {
            initLetter(rx, rw);
        } else {
            initBeuglante(rx, rw);
        }

        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(rx + 128, top + 186, rw - 128, 18).build());
        updateButtons();

        setInitialFocus(recipientText.isEmpty() ? recipientBox : (mode == Mode.LETTER ? bodyBox : cryBox));
    }

    private void initLetter(int rx, int rw) {
        titleBox = new EditBox(font, rx, top + 74, rw, 16, Component.literal("Objet"));
        titleBox.setMaxLength(maxTitle);
        titleBox.setHint(Component.literal("Objet de la missive").withStyle(ChatFormatting.DARK_GRAY));
        titleBox.setValue(titleText);
        addRenderableWidget(titleBox);

        bodyBox = new MultiLineEditBox(font, rx, top + 104, rw, 64,
                Component.literal("Ma très chère..."), Component.literal("Missive"));
        bodyBox.setCharacterLimit(maxBody);
        bodyBox.setValue(bodyText);
        addRenderableWidget(bodyBox);

        sendButton = addRenderableWidget(Button.builder(Component.literal("✉ Envoyer le hibou"), b -> sendLetter())
                .bounds(rx, top + 186, 124, 18).build());
    }

    private void initBeuglante(int rx, int rw) {
        cryBox = new EditBox(font, rx, top + 74, rw, 16, Component.literal("Cri"));
        cryBox.setMaxLength(100);
        cryBox.setHint(Component.literal("Ce qu'elle hurlera (lu par tous)").withStyle(ChatFormatting.DARK_GRAY));
        cryBox.setValue(cryText);
        addRenderableWidget(cryBox);

        int bw = (rw - 6) / 4;
        recButton = addRenderableWidget(Button.builder(Component.literal("● Enreg.").withStyle(ChatFormatting.RED), b -> startRec())
                .bounds(rx, top + 104, bw, 16)
                .tooltip(Tooltip.create(Component.literal("Enregistrer votre voix au micro (" + duration(maxSeconds) + " max)")))
                .build());
        stopButton = addRenderableWidget(Button.builder(Component.literal("■ Stop"), b -> {
                    BeuglanteAudio.stopRecording();
                    BeuglanteAudio.stopPreview();
                })
                .bounds(rx + bw + 2, top + 104, bw, 16).build());
        playButton = addRenderableWidget(Button.builder(Component.literal("▶ Écouter"), b -> BeuglanteAudio.preview())
                .bounds(rx + 2 * (bw + 2), top + 104, bw, 16)
                .tooltip(Tooltip.create(Component.literal("Réécouter votre enregistrement avant de l'envoyer")))
                .build());
        clearButton = addRenderableWidget(Button.builder(Component.literal("✖ Effacer"), b -> {
                    BeuglanteAudio.clear();
                    status = Component.empty();
                })
                .bounds(rx + 3 * (bw + 2), top + 104, bw, 16).build());
        micButton = addRenderableWidget(Button.builder(micLabel(), b -> {
                    BeuglanteAudio.nextDevice();
                    b.setMessage(micLabel());
                    setStatus("Micro choisi : " + BeuglanteAudio.deviceName(), true);
                })
                .bounds(rx, top + 132, rw, 14)
                .tooltip(Tooltip.create(Component.literal("Cliquer pour changer de micro si le vôtre n'est pas capté.")))
                .build());

        sendButton = addRenderableWidget(Button.builder(
                        Component.literal("‼ Envoyer la beuglante").withStyle(ChatFormatting.RED), b -> sendBeuglante())
                .bounds(rx, top + 186, 124, 18).build());
    }

    private Component micLabel() {
        return Component.literal("Micro : " + font.plainSubstrByWidth(BeuglanteAudio.deviceName(), 150));
    }

    /** 125 -> "2:05" */
    private static String duration(float seconds) {
        int total = (int) seconds;
        return total / 60 + ":" + String.format(Locale.ROOT, "%02d", total % 60);
    }

    private void switchMode(Mode m) {
        if (mode == m) return;
        if (m == Mode.LETTER) {
            BeuglanteAudio.stopRecording();
            BeuglanteAudio.stopPreview();
        }
        mode = m;
        status = Component.empty();
        rebuildWidgets();
    }

    // ------------------------------------------------------------------
    // Carnet de contacts
    // ------------------------------------------------------------------

    /** Le contact correspondant au texte du destinataire (numéro ou nom), s'il est dans le carnet. */
    private Payloads.DirectoryEntry currentContact() {
        String q = recipientBox == null ? "" : recipientBox.getValue().trim();
        if (q.isEmpty()) return null;
        String digits = q.replaceFirst("^(?i)(#|n°|no\\.?|n\\.)\\s*", "");
        for (Payloads.DirectoryEntry e : contacts) {
            if (digits.matches("\\d{1,9}") && Integer.parseInt(digits) == e.number()) return e;
            if (e.name().equalsIgnoreCase(q)) return e;
        }
        return null;
    }

    private void updateContactButton() {
        if (contactButton == null) return;
        String q = recipientBox.getValue().trim().replaceFirst("^(?i)(#|n°|no\\.?|n\\.)\\s*", "");
        if (currentContact() != null) {
            contactButton.setMessage(Component.literal("✖ Retirer"));
            contactButton.active = true;
            contactButton.setTooltip(Tooltip.create(Component.literal("Rayer ce contact de votre carnet")));
        } else {
            contactButton.setMessage(Component.literal("★ Ajouter"));
            contactButton.active = q.matches("\\d{1,9}");
            contactButton.setTooltip(Tooltip.create(Component.literal(
                    "Inscrire ce numéro de hibou dans votre carnet.\nLes numéros sont secrets : demandez-le au joueur.")));
        }
    }

    private void toggleContact() {
        Payloads.DirectoryEntry existing = currentContact();
        if (existing != null) {
            PacketDistributor.sendToServer(new Payloads.RemoveContact(existing.number()));
            return;
        }
        String q = recipientBox.getValue().trim().replaceFirst("^(?i)(#|n°|no\\.?|n\\.)\\s*", "");
        if (q.matches("\\d{1,9}")) {
            PacketDistributor.sendToServer(new Payloads.AddContact(Integer.parseInt(q), ""));
        }
    }

    public void onContacts(Payloads.ContactsUpdate update) {
        contacts.clear();
        contacts.addAll(update.contacts());
        setStatus(update.message(), true);
        rebuildWidgets();
    }

    // ------------------------------------------------------------------
    // Envoi
    // ------------------------------------------------------------------

    private void updateButtons() {
        if (sendButton == null) return;
        if (mode == Mode.LETTER) {
            sendButton.active = !waiting && blankLetters > 0;
            sendButton.setTooltip(blankLetters > 0 ? null
                    : Tooltip.create(Component.literal("Il vous faut une lettre vierge (papier + plume).\n"
                    + "Ou écrivez dans un livre et plume puis /hibou envoyer <contact>.")));
        } else {
            boolean rec = BeuglanteAudio.isRecording();
            boolean has = BeuglanteAudio.recordedSeconds() > 0.1f;
            sendButton.active = !waiting && beuglantes > 0 && !rec;
            sendButton.setTooltip(beuglantes > 0 ? null
                    : Tooltip.create(Component.literal("Il vous faut une beuglante vierge, ou 1 papier + 1 poudre à canon.")));
            if (recButton != null) {
                recButton.active = !rec;
                stopButton.active = rec || BeuglanteAudio.isPreviewing();
                playButton.active = !rec && has;
                clearButton.active = !rec && has;
                micButton.active = !rec;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
        if (mode != Mode.BEUGLANTE) return;

        boolean rec = BeuglanteAudio.isRecording();
        if (rec && BeuglanteAudio.seemsSilent()) {
            setStatus("Aucun son capté ! Vérifiez votre micro (bouton Micro) et que Windows autorise "
                    + "les applications de bureau à utiliser le micro.", false);
        }
        if (wasRecording && !rec) {
            // l'enregistrement vient de s'arrêter : on vérifie qu'il est bon
            String err = BeuglanteAudio.captureError();
            if (err != null) {
                setStatus(err, false);
            } else if (BeuglanteAudio.seemsSilent()) {
                setStatus("L'enregistrement est muet : aucun son n'a été capté. Changez de micro ou vérifiez "
                        + "l'accès au micro dans Windows, puis réessayez.", false);
            } else if (BeuglanteAudio.reachedMax()) {
                setStatus("Durée maximale atteinte (" + duration(maxSeconds) + "). Écoutez-la avant d'envoyer.", true);
            } else {
                setStatus("Enregistrement prêt : " + duration(BeuglanteAudio.recordedSeconds())
                        + ". Écoutez-le avant d'envoyer.", true);
            }
        }
        wasRecording = rec;
    }

    private void startRec() {
        String error = BeuglanteAudio.startRecording(maxSeconds);
        if (error != null) {
            setStatus(error, false);
        } else {
            setStatus("Parlez fort, la beuglante vous écoute... (" + BeuglanteAudio.formatInfo() + ")", true);
        }
    }

    private void sendLetter() {
        String recipient = recipientBox.getValue().trim();
        if (recipient.isEmpty()) {
            setStatus("Indiquez à qui votre hibou doit porter la missive.", false);
            return;
        }
        if (bodyBox.getValue().isBlank()) {
            setStatus("Votre missive est encore vierge.", false);
            return;
        }
        waiting = true;
        updateButtons();
        setStatus("Le hibou se prépare...", true);
        PacketDistributor.sendToServer(new Payloads.SendLetter(recipient, titleBox.getValue(), bodyBox.getValue()));
    }

    private void sendBeuglante() {
        String recipient = recipientBox.getValue().trim();
        if (recipient.isEmpty()) {
            setStatus("Indiquez à qui la beuglante doit hurler.", false);
            return;
        }
        if (cryBox.getValue().isBlank()) {
            setStatus("Écrivez le cri de la beuglante : tout le monde le lira.", false);
            return;
        }
        BeuglanteAudio.stopRecording();
        BeuglanteAudio.stopPreview();
        if (BeuglanteAudio.seemsSilent()) {
            setStatus("Votre enregistrement est muet. Effacez-le (✖) pour envoyer le cri seul, ou réenregistrez.", false);
            return;
        }
        byte[] audio = BeuglanteAudio.recordedUlaw();
        int uploadId = -1;
        if (audio != null) {
            uploadId = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
            int total = (audio.length + CHUNK - 1) / CHUNK;
            for (int i = 0; i < total; i++) {
                int from = i * CHUNK;
                int len = Math.min(CHUNK, audio.length - from);
                byte[] part = new byte[len];
                System.arraycopy(audio, from, part, 0, len);
                PacketDistributor.sendToServer(new Payloads.BeuglanteChunk(uploadId, i, total, part));
            }
        }
        waiting = true;
        updateButtons();
        setStatus("La beuglante se met à fumer...", true);
        PacketDistributor.sendToServer(new Payloads.SendBeuglante(recipient, cryBox.getValue(), uploadId));
    }

    /** Réponse du serveur à l'envoi. */
    public void onSendResult(Payloads.SendResult result) {
        waiting = false;
        if (mode == Mode.LETTER) blankLetters = result.blankLetters();
        else beuglantes = result.blankLetters();
        if (result.success()) {
            if (mode == Mode.BEUGLANTE) BeuglanteAudio.clear();
            onClose();
        } else {
            setStatus(result.message(), false);
            updateButtons();
        }
    }

    private void setStatus(String text, boolean ok) {
        status = Component.literal(text);
        statusColor = ok ? Parchment.INK_FAINT : Parchment.CRIMSON;
    }

    @Override
    public void removed() {
        BeuglanteAudio.stopRecording();
        BeuglanteAudio.stopPreview();
        super.removed();
    }

    // ------------------------------------------------------------------
    // Rendu
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Parchment.draw(g, left, top, W, H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // En-tête
        g.drawString(font, "✦ Volière Royale ✦", left + 12, top + 8, Parchment.CRIMSON, false);
        String info = mode == Mode.LETTER
                ? "Votre hibou : n°" + LetterContent.formatNumber(myNumber) + "    Lettres vierges : " + blankLetters
                : "Votre hibou : n°" + LetterContent.formatNumber(myNumber) + "    Beuglantes : " + beuglantes;
        int available = mode == Mode.LETTER ? blankLetters : beuglantes;
        g.drawString(font, info, left + (W - font.width(info)) / 2, top + 20, available > 0 ? Parchment.INK : Parchment.CRIMSON, false);
        Parchment.rule(g, left + 10, left + W - 10, top + 30);

        // Colonne gauche
        g.drawString(font, "Carnet", left + 10, top + 34, Parchment.INK, false);
        if (contacts.isEmpty()) {
            g.drawWordWrap(font, Component.literal("Votre carnet est vide. Demandez son numéro à un joueur, "
                            + "tapez-le à droite puis « ★ Ajouter »."),
                    left + 10, top + 48, 98, Parchment.INK_FAINT);
        }
        int pages = Math.max(1, (contacts.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        String pageText = (page + 1) + "/" + pages;
        g.drawString(font, pageText, left + 59 - font.width(pageText) / 2, top + 171, Parchment.INK, false);
        g.fill(left + 114, top + 34, left + 115, top + H - 8, Parchment.GOLD);

        // Colonne droite
        int rx = left + 118;
        int rw = W - 118 - 10;
        g.drawString(font, "Destinataire", rx, top + 34, Parchment.INK, false);
        if (mode == Mode.LETTER) {
            g.drawString(font, "Objet", rx, top + 64, Parchment.INK, false);
            g.drawString(font, "Missive", rx, top + 94, Parchment.INK, false);
            String count = bodyBox.getValue().length() + "/" + maxBody;
            g.drawString(font, count, left + W - 10 - font.width(count), top + 94, Parchment.INK_FAINT, false);
        } else {
            g.drawString(font, "Cri (lu par tous)", rx, top + 64, Parchment.CRIMSON, false);
            g.drawString(font, "Voix (facultatif)", rx, top + 94, Parchment.INK, false);
            String time = duration(BeuglanteAudio.recordedSeconds()) + " / " + duration(maxSeconds);
            g.drawString(font, time, left + W - 10 - font.width(time), top + 94,
                    BeuglanteAudio.isRecording() ? Parchment.CRIMSON : Parchment.INK_FAINT, false);

            // barre : durée enregistrée + niveau du micro
            int barY = top + 124;
            g.fill(rx, barY, rx + rw, barY + 4, 0xFF8B7350);
            int filled = (int) (rw * Math.min(1f, BeuglanteAudio.recordedSeconds() / maxSeconds));
            g.fill(rx, barY, rx + filled, barY + 4, Parchment.CRIMSON);
            if (BeuglanteAudio.isRecording()) {
                int lvl = (int) (rw * Math.min(1f, BeuglanteAudio.level() * 2f));
                g.fill(rx, barY + 5, rx + lvl, barY + 7, Parchment.GOLD);
            }
            g.drawWordWrap(font, Component.literal("Tous ceux qui sont proches liront le cri et entendront votre voix."),
                    rx, top + 150, rw, Parchment.INK_FAINT);
        }

        // Statut
        List<FormattedCharSequence> lines = font.split(status, rw);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), rx, top + 170 + i * 8, statusColor, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
