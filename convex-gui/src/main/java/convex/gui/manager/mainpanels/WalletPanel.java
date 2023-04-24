package convex.gui.manager.mainpanels;

import java.awt.BorderLayout;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.crypto.AKeyPair;
import convex.core.crypto.WalletEntry;
import convex.core.data.Address;
import convex.gui.components.ActionPanel;
import convex.gui.components.ScrollyList;
import convex.gui.components.WalletComponent;
import convex.gui.manager.PeerGUI;
import convex.peer.Server;

@SuppressWarnings("serial")
public class WalletPanel extends JPanel {
	
	private static final Logger log = LoggerFactory.getLogger(WalletPanel.class.getName());


	public static WalletEntry HERO;

	private static DefaultListModel<WalletEntry> listModel = new DefaultListModel<>();;
	ScrollyList<WalletEntry> walletList;

	public void addWalletEntry(WalletEntry we) {
		listModel.addElement(we);
	}

	/**
	 * Create the panel.
	 */
	public WalletPanel() {
		setLayout(new BorderLayout(0, 0));

		JPanel toolBar = new ActionPanel();
		add(toolBar, BorderLayout.SOUTH);

		// new wallet button
		JButton btnNew = new JButton("New");
		toolBar.add(btnNew);
		btnNew.addActionListener(e -> {
			Server s=PeerGUI.getDefaultPeer().peerServer;
			Convex convex=Convex.connect(s, HERO.getAddress(), HERO.getKeyPair());
			AKeyPair newKP=AKeyPair.generate();
			try {
				Address addr=convex.createAccountSync(newKP.getAccountKey());
				listModel.addElement(WalletEntry.create(addr,newKP));
			} catch (Throwable t) {
				log.warn("Exception creating account: ",e);
			}
		});

		// inital list
        HERO = WalletEntry.create(PeerGUI.getUserAddress(0), PeerGUI.getUserKeyPair(0));
		addWalletEntry(HERO);
		//addWalletEntry(WalletEntry.create(PeerGUI.getUserAddress(1), PeerGUI.getUserKeyPair(1)));
		//addWalletEntry(WalletEntry.create(PeerGUI.getUserAddress(2),PeerGUI.getUserKeyPair(2)));

		// create and add ScrollyList
		walletList = new ScrollyList<WalletEntry>(listModel, we -> new WalletComponent(we));
		add(walletList, BorderLayout.CENTER);
	}

	public static ListModel<WalletEntry> getListModel() {
		return listModel;
	}

}
